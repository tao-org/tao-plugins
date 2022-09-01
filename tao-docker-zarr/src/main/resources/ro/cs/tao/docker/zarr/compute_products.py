#!/usr/bin/env python3
import argparse
import logging
import multiprocessing.dummy
import os
import re
import sqlite3
import subprocess
from datetime import datetime, timedelta


class ProductInfo:
    def __init__(self, path, name, tile_id, ty, orbit_type, polarization, date, scale, offset):
        self.path = path
        self.name = name
        self.tile_id = tile_id
        self.ty = ty
        self.orbit_type = orbit_type
        self.polarization = polarization
        self.date = date
        self.scale = scale
        self.offset = offset
        self.output = None


def get_band_name(ty):
    if ty == TYPE_BACKSCATTER or ty == TYPE_BACKSCATTER_COMPOSITE:
        return "backscatter"
    elif ty == TYPE_COHERENCE or ty == TYPE_COHERENCE_COMPOSITE:
        return "coherence"
    elif ty == TYPE_FAPAR:
        return "fapar"
    elif ty == TYPE_FCOVER:
        return "fcover"
    elif ty == TYPE_LAI:
    	return "lai"
    elif ty == TYPE_NDVI:
    	return "ndvi"

def process_product(product, out):
    product.output = os.path.join(out, product.name + ".zarr")
    band_name = get_band_name(product.ty)
    command = ["./launcher.py", "--input", product.path, "--date", product.date.isoformat(), "--band-name", band_name]
    if product.scale:
        command += ["--scale", str(product.scale)]
    if product.offset:
        command += ["--offset", str(product.offset)]
    command += ["--out", product.output]
    print(command)
    subprocess.call(command)
    #product.output = os.path.join(out, product.name + ".zarr.zip")
    return product

parser = argparse.ArgumentParser(description="Convert products from folder")
parser.add_argument('--folder', required=True)
parser.add_argument('--out', required=True)
args = parser.parse_args()

folder = args.folder
out = args.out

TYPE_BACKSCATTER = 1
TYPE_COHERENCE = 2
TYPE_FAPAR = 3
TYPE_FCOVER = 4
TYPE_LAI = 5
TYPE_NDVI = 6
TYPE_BACKSCATTER_COMPOSITE = 7
TYPE_COHERENCE_COMPOSITE = 8

POLARIZATION_VV = 1
POLARIZATION_VH = 2

ORBIT_TYPE_ASC = 1
ORBIT_TYPE_DESC = 2

pool = multiprocessing.dummy.Pool()
res = []
for root, dirs, files in os.walk(folder, topdown=True, followlinks=True):
    for file in files:
        file_path = os.path.join(root, file)
        (name, ext) = os.path.splitext(file)
        if ext != ".tif" and ext != ".TIF":
            continue
        if name.endswith("_MASK") or name.endswith("_AF"):
            continue

        ty = None
        if "_BCK_" in name:
            ty = TYPE_BACKSCATTER
        elif "_COH_" in name:
            ty = TYPE_COHERENCE
        elif "_BCK" in name:
            ty = TYPE_BACKSCATTER_COMPOSITE
        elif "_COHE" in name:
            ty = TYPE_COHERENCE_COMPOSITE
        elif "_SFAPAR" in name:
            ty = TYPE_FAPAR
        elif "_SFCOVER" in name:
            ty = TYPE_FCOVER
        elif "_SLAI" in name:
            ty = TYPE_LAI
        elif "_SNDVI" in name:
            ty = TYPE_NDVI
        else:
            logging.error("Unknown product type: %s", name)
            continue

        if ty in [TYPE_FAPAR, TYPE_FCOVER, TYPE_NDVI, TYPE_LAI]:
            if "TILES" not in file_path:
                continue
            if "IMG_DATA" not in file_path:
                continue

        orbit_type = None
        if "_ASC_" in name:
            orbit_type = ORBIT_TYPE_ASC
        elif "_DESC_" in name:
            orbit_type = ORBIT_TYPE_DESC

        polarization = None
        if ty in [TYPE_BACKSCATTER, TYPE_COHERENCE, TYPE_BACKSCATTER_COMPOSITE, TYPE_COHERENCE_COMPOSITE]:
            if "_VV_" in name:
                polarization = POLARIZATION_VV
            elif "_VH_" in name:
                polarization = POLARIZATION_VH
            else:
                logging.error("Unknown polarization: %s", name)
                continue

        date = None
        scale = None
        offset = None
        tile_id = None
        if ty in [TYPE_BACKSCATTER, TYPE_COHERENCE]:
            m = re.search("_(\d{8}T\d{6})_", name)
            if m:
                date_str = m.group(1)
                date = datetime.strptime(date_str, "%Y%m%dT%H%M%S")
        elif ty in [TYPE_FAPAR, TYPE_FCOVER, TYPE_NDVI, TYPE_LAI]:
            m = re.search("_A(\d{8}T\d{6})_", name)
            if m:
                date_str = m.group(1)
                date = datetime.strptime(date_str, "%Y%m%dT%H%M%S")

        if ty in [TYPE_BACKSCATTER_COMPOSITE, TYPE_COHERENCE_COMPOSITE]:
            m = re.search("_W(\d{4})(\d{2})_T(\d{2}[A-Z]{3})", name)
            if m:
                year = int(m.group(1))
                week = int(m.group(2))
                tile_id = m.group(3)
                date = datetime(year, 1, 1) + timedelta(weeks=week - 1)
                scale = 1000

        if ty in [TYPE_BACKSCATTER, TYPE_COHERENCE]:
            pos = name.rfind('_')
            tile_id = name[pos + 1:]
        elif ty in [TYPE_FAPAR, TYPE_FCOVER, TYPE_NDVI, TYPE_LAI]:
            pos = name.rfind('_')
            tile_id = name[pos + 2:]
            scale = 1000

        product = ProductInfo(file_path, name, tile_id, ty, orbit_type, polarization, date, scale, offset)
        res.append(pool.apply_async(process_product, (product, out)))
        print(f"Appended product {product.name}, tile id {tile_id}")

pool.close()

conn = sqlite3.connect(os.path.join(args.out, "products.db"))
cursor = conn.cursor()
cursor.execute("create table if not exists products(name text primary key, path text, tile_id text, type smallint, orbit_type smallint, polarization smallint, date text)")

for r in res:
    product = r.get()
    try:
        cursor.execute("insert into products values(?, ?, ?, ?, ?, ?, ?)", (product.name, product.output, product.tile_id, product.ty, product.orbit_type, product.polarization, product.date))
        #uncompressed = os.path.splitext(product.output)[0]
        #shutil.rmtree(uncompressed)
        #print(f"Removed {uncompressed}")
    except Exception as e:
        print(f"Cannot insert in the db the {product.name} product. Error {e}")

conn.commit()

pool.join()
