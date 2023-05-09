#!/usr/bin/env python3

import argparse
import configparser
import docker
import os
import psycopg2
import re
import sqlite3
from datetime import datetime
from lxml import etree

DOCKER_IMAGE = "sen4x/gdal_to_xarray:0.1"

# Constants used for parsing the metadata files
SCALE_XPATH_SENTINEL1 = "scaling"
SCALE_XPATH_L3B = "General_Info/Product_Image_Characteristics/QUANTIFICATION_VALUE"
FILL_VALUES_XPATH_L3B = "General_Info/Product_Image_Characteristics/Special_Values/SPECIAL_VALUE_INDEX"

# Constants used for determining the category of the products
SENTINEL_1_BACKSCATTER = "BACKSCATTER"
SENTINEL_1_COHERENCE = "COHERENCE"
SENTINEL_2_LAI = "LAI"
SENTINEL_2_NDVI = "NDVI"
SENTINEL_2_FAPAR = "FAPAR"
SENTINEL_2_FCOVER = "FCOVER"
POLARIZATION_VV = 1
POLARIZATION_VH = 2

# Parse command-line arguments
parser = argparse.ArgumentParser(description="Retrieve info products from database")
parser.add_argument('--db', required=False, default='products.db')
parser.add_argument('--config', required=True)
parser.add_argument('--script-path', required=False, default='/home/lidia/scripts/convertor/gdal_to_xarray.py')
parser.add_argument('--out', required=False, default='/home/lidia/outputs/dbreader_output')
parser.add_argument('--mounts', required=False, action='store', type=str, nargs="+")
args = parser.parse_args()

db_name = args.db
db = os.path.join(args.out, db_name)
output_dir = args.out
mounts = args.mounts
script_path = args.script_path
config_file = args.config


# TODO: Maybe upgrade this function?
def parse_metadata(path, product_type):
    tree = etree.parse(path)
    add_offset = None
    if product_type in [10, 11]:
        scale_attrs = tree.xpath(SCALE_XPATH_SENTINEL1)
        fill_value = 0
    elif product_type in [3]:
        scale_attrs = tree.xpath(SCALE_XPATH_L3B)
        fill_values = tree.xpath(FILL_VALUES_XPATH_L3B)
        fill_value = fill_values[0].text
    scaling = scale_attrs[0].text
    return (scaling, fill_value, add_offset)


def get_band_name(path, product_type):
    filename = os.path.basename(path)
    if product_type in [10, 11]:
        if re.search("^S1[A-Z]_L2_BCK_\w.*", filename):
            return SENTINEL_1_BACKSCATTER
        if re.search("^S1[A-Z]_L2_COH_\w.*", filename):
            return SENTINEL_1_COHERENCE
    elif product_type in [3]:
        if re.search("^S2[A-Z]*_L3(A|B)_SFAPAR\w.*", filename):
            return SENTINEL_2_FAPAR
        if re.search("^S2[A-Z]*_L3(A|B)_SFCOVER\w.*", filename):
            return SENTINEL_2_FCOVER
        if re.search("^S2[A-Z]*_L3(A|B)_SLAI\w.*", filename):
            return SENTINEL_2_LAI
        if re.search("^S2[A-Z]*_L3(A|B)_NDVI\w.*", filename):
            return SENTINEL_2_NDVI


def find_polarization(path):
    polarization = None
    name = os.path.basename(path)
    if os.path.isfile(path):
        if "_VV_" in name:
                polarization = POLARIZATION_VV
        elif "_VH_" in name:
            polarization = POLARIZATION_VH
    return polarization


def find_metadata_path(path, tile_id):
    if os.path.isfile(path):
        parent_dir = os.path.dirname(os.path.abspath(path))
        pattern = f".*{tile_id}.(mtd|xml|MTD|XML)$"
    else:
        parent_dir = path
        pattern = ".*.(mtd|xml|MTD|XML)$"
    if os.path.exists(parent_dir):
        metadata_files = [f for f in os.listdir(parent_dir) if re.search(pattern, f)]
        if len(metadata_files) == 0:
            print(f"Cannot find metadata file in {parent_dir}")
        else:
            print(f"Found metadata at {os.path.join(parent_dir, metadata_files[0])}")
            return os.path.join(parent_dir, metadata_files[0])
    return None


def find_products(path):
    if os.path.isfile(path):
        return [path]

    products = []
    for root, _, files in os.walk(path, topdown=True):
        for file in files:
            file_path = os.path.join(root, file)
            (name, ext) = os.path.splitext(file)
            if ext != ".tif" and ext != ".TIF":
                continue
            if name.endswith("_MASK") or name.endswith("_AF"):
                continue
            if "TILES" not in file_path:
                continue
            if "IMG_DATA" not in file_path:
                continue
            products.append(file_path)
    return products


def read_config_file(path):
    config_obj = configparser.ConfigParser()
    config_obj.read(path)
    db_params = config_obj["postgresql"]
    return db_params

def read_products_from_sen4cap(ids, db_params):
    product_type_ids = ", ".join(str(x) for x in ids)
    conn = None
    products = []
    try:
        conn = psycopg2.connect(
            host=db_params["host"],
            database=db_params["database"],
            user=db_params["user"],
            password=db_params["password"])

        cursor = conn.cursor()
        cursor.execute(f"select id, product_type_id, site_id, name, tiles, full_path, created_timestamp, footprint, orbit_id from product where product_type_id in ({product_type_ids})")
        # products = cursor.fetchall()
        products = cursor.fetchmany(3)
        cursor.close()
    except (Exception, psycopg2.DatabaseError) as error:
        print(error)
    finally:
        if conn is not None:
            conn.close()
    return products


def write_products_to(db, products):
    conn = sqlite3.connect(db)
    cursor = conn.cursor()
    cursor.execute("create table if not exists product (id INTEGER PRIMARY KEY, product_type SMALLINT, site_id SMALLINT, full_path TEXT, created_timestamp TIMESTAMP, footprint TEXT, orbit_id SMALLINT)")
    cursor.execute("create table if not exists product_band (product_id INTEGER, tile TEXT, name TEXT, band_index SMALLINT, polarisation TEXT, scaling_factor FLOAT, fill_value FLOAT, add_offset FLOAT, path TEXT, FOREIGN KEY(product_id) REFERENCES product(id))")

    for product in products:
        (product_id, product_type, site_id, name, tiles, full_path, created_timestamp, footprint, orbit_id) = product
        tile_id = tiles[0]
        products_path = find_products(full_path)
        metadata_path = find_metadata_path(full_path, tile_id)
        polarisation = find_polarization(full_path)

        # Insert entry to the product table
        try:
            cursor.execute("insert into product values (?, ?, ?, ?, ?, ?, ?)", (product_id, product_type, site_id, full_path, created_timestamp, footprint, orbit_id))
        except Exception as e:
            print(f"Cannot insert in the db the {name} product. Error {e}")

        if metadata_path:
            (scaling_factor, fill_value, add_offset) = parse_metadata(metadata_path, product_type)
            for p in products_path:
                band_name = get_band_name(p, product_type).lower()
                band_index = 1
                # Insert product band into the product_band table
                try:
                    query = "INSERT INTO product_band (product_id, tile, name, band_index, polarisation, scaling_factor, fill_value, add_offset, path) values (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    cursor.execute(query, (product_id, tile_id, band_name, band_index, polarisation, scaling_factor, fill_value, add_offset, p))
                    print(f"Inserted {p} into product_band table.")
                except Exception as e:
                    print(f"Cannot insert in the product_band the {p} product. Error {e}")

    conn.commit()
    conn.close()

class Product:
    def __init__(self, path, tile_id, band_index, product_type, orbit_type, site_id, footprint, polarisation, band_name, scaling_factor, fill_value, add_offset, timestamp, output_dir):
        self.path = path
        self.name = os.path.basename(path)
        self.band_index = band_index
        self.tile_id = tile_id
        self.product_type = product_type
        self.orbit_type = orbit_type
        self.site_id = site_id
        self.footprint = footprint
        self.polarisation = polarisation
        self.band_name = band_name
        self.scaling_factor = scaling_factor
        self.fill_value = fill_value
        self.add_offset = add_offset
        print(timestamp)
        self.timestamp = datetime.strptime(timestamp, "%Y-%m-%d %H:%M:%S")
        print(self.timestamp)
        self.output = output_dir

    def get_output_path(self):
        (name, _) = os.path.splitext(os.path.basename(self.path))
        filename = name + '.zarr'
        output_file_path = os.path.join(self.output, filename)
        print(output_file_path)
        return output_file_path

    def convert_to_zarr(self, mounts, script_path):
        client = docker.from_env()
        user_id = os.getuid()
        groups = [os.stat("/var/run/docker.sock").st_gid]
        command = [script_path, "--input", self.path, "--out", self.get_output_path(), "--date", self.timestamp.isoformat(), "--band-name", self.band_name]
        if self.scaling_factor:
            command += ["--scale", str(self.scaling_factor)]
        if self.fill_value:
            command += ["--fill", str(self.fill_value)]
        if self.add_offset:
            command += ["--offset", str(self.add_offset)]
        volumes = {}
        for m in mounts:
            volumes[m] = {
                'bind': m,
                'mode': 'rw'
            }
        volumes[script_path] = {
            'bind': script_path,
            'mode': 'ro'
        }
        container = client.containers.run(DOCKER_IMAGE, command=command, remove=True, stderr=True, user=user_id, volumes=volumes, group_add=groups, detach=True)
        response = container.wait()
        status_code = response['StatusCode']
        print(f"Status code: {status_code}")


def process_products_from(db, output_dir, script_path):
    conn = sqlite3.connect(db)
    cursor = conn.cursor()
    cursor.execute("select product_id, tile, name, band_index, polarisation, scaling_factor,fill_value, add_offset, path from product_band")
    products_bands = cursor.fetchall()
    for product_band in products_bands:
        (product_id, tile, band_name, band_index, polarisation, scaling_factor, fill_value, add_offset, file_path) = product_band
        cursor.execute(f"select product_type, site_id, datetime(created_timestamp), footprint, orbit_id from product where id={product_id}")
        product_info = cursor.fetchone()
        (product_type, site_id, created_timestamp, footprint, orbit_id) = product_info
        product = Product(file_path, tile, band_index, product_type, orbit_id, site_id, footprint, polarisation, band_name, scaling_factor, fill_value, add_offset, created_timestamp, output_dir)
        # convert the product to zarr
        product.convert_to_zarr(mounts, script_path)

db_params = read_config_file(config_file)
products = read_products_from_sen4cap([3, 10, 11], db_params)
write_products_to(db, products)
process_products_from(db, output_dir, script_path)
