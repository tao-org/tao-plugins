#!/usr/bin/env python3
import argparse
import docker
import itertools
import logging
import numpy as np
import os
import shutil
import sqlite3
import xarray as xr

DOCKER_IMAGE = "sen4x/gdal_to_xarray:0.1"

def stack_products(products, out, name, mounts):
    client = docker.from_env()
    user_id = os.getuid()
    groups = [os.stat("/var/run/docker.sock").st_gid]
    command = ["union_zarrs.py", "--band-name", name, "--out", out, "--inputs"] + products
    volumes = {}
    for m in mounts:
        volumes[m] = {
            'bind': m,
            'mode': 'rw'
        }

    container = client.containers.run(DOCKER_IMAGE, command=command, remove=True, stderr=True, user=user_id, volumes=volumes, group_add=groups, detach=True)
    response = container.wait()
    status_code = response['StatusCode']
    print(f"Status code: {status_code}")



parser = argparse.ArgumentParser(description="Stack Zarr products")
parser.add_argument('--db', required=True, help="Product database")
parser.add_argument('--out', required=True, help="Output directory root")
parser.add_argument('--mounts', required=False, action='store', type=str, nargs="+")
args = parser.parse_args()

TYPE_BACKSCATTER = 1
TYPE_COHERENCE = 2
TYPE_FAPAR = 3
TYPE_FCOVER = 4
TYPE_LAI = 5
TYPE_NDVI = 6
TYPE_BACKSCATTER_COMPOSITE = 7
TYPE_COHERENCE_COMPOSITE = 8

ORBIT_TYPE_ASC = 1
ORBIT_TYPE_DESC = 2

POLARIZATION_VV = 1
POLARIZATION_VH = 2

conn = sqlite3.connect(args.db)
cursor = conn.cursor()

cursor.execute("select distinct type, orbit_type, polarization, tile_id from products")
groups = cursor.fetchall()

def get_united_prod(products, name, output):
    prods = [p[0] for p in products]
    if len(prods) == 1:
        return (prods[0], False)

    data = []
    output_ds = None
    for p in prods:
        with xr.open_zarr(p) as ds:
            if output_ds is None:
                filename = os.path.basename(p)
                (fn, ext) = os.path.splitext(filename)
                fn = fn + '_tmp'
                output_ds = ds.copy(deep=True)
            values = ds[name].values
            filtered = np.where(values == 0, np.nan, values)
            data.append(filtered)

    a = np.nanmean(data, axis=0)
    avg = np.nan_to_num(a)

    output_ds[name].values = avg
    output_path = os.path.join(output, fn) + ext
    output_ds.to_zarr(output_path)
    return (output_path, True)

for group in groups:
    products_to_unite = []
    temps = []
    cursor.execute("select path, date(date) from products where (type, orbit_type, polarization, tile_id) is (?, ?, ?, ?) order by date", group)
    products = cursor.fetchall()

    (ty, orbit_type, polarization, tile_id) = group

    ty_str = None
    name = None
    if ty == TYPE_BACKSCATTER:
        ty_str = "BCK"
        name = "backscatter"
    elif ty == TYPE_COHERENCE:
        ty_str = "COH"
        name = "coherence"
    elif ty == TYPE_FAPAR:
        ty_str = "FAPAR"
        name = "fapar"
    elif ty == TYPE_FCOVER:
        ty_str = "FCOVER"
        name = "fcover"
    elif ty == TYPE_LAI:
        ty_str = "LAI"
        name = "lai"
    elif ty == TYPE_NDVI:
        ty_str = "NDVI"
        name = "ndvi"
    elif ty == TYPE_BACKSCATTER_COMPOSITE:
        ty_str = "BCK"
        name = "backscatter"
    elif ty == TYPE_COHERENCE_COMPOSITE:
        ty_str = "COHE"
        name = "coherence"
    else:
        logging.error("Unknown product type id %s", ty)

    orbit_type_str = None
    if orbit_type == ORBIT_TYPE_ASC:
        orbit_type_str = "ASC"
    elif orbit_type == ORBIT_TYPE_DESC:
        orbit_type_str = "DESC"
    elif orbit_type:
        logging.error("Unknown orbit type id %s", orbit_type)

    polarization_str = None
    if polarization == POLARIZATION_VV:
        polarization_str = "VV"
    elif polarization == POLARIZATION_VH:
        polarization_str = "VH"
    elif polarization:
        logging.error("Unknown polarization id %s", polarization)

    if ty in [TYPE_BACKSCATTER, TYPE_COHERENCE]:
        output = f"S1_L2_{ty_str}_{polarization_str}_{tile_id}"
    elif ty in [TYPE_FAPAR, TYPE_FCOVER, TYPE_NDVI, TYPE_LAI]:
        output = f"S2_L3B_{ty_str}_{tile_id}"
    elif ty in [TYPE_BACKSCATTER_COMPOSITE, TYPE_COHERENCE_COMPOSITE]:
	    output = f"S1_L2COMP_{ty_str}_{orbit_type_str}_{polarization_str}_W_{tile_id}"
    else:
        logging.error("Unknown type id %s", ty)

    # Group by date, which is the second element retrieved from the database, hence x[1]
    for g in itertools.groupby(products, lambda x: x[1]):
        (path_to_unite, isTemp) = get_united_prod(g[1], name, args.out)
        products_to_unite += [path_to_unite]
        if isTemp:
            temps.append(path_to_unite)

    stack_products(products_to_unite, os.path.join(args.out, output + ".zarr"), name, args.mounts)

    for t in temps:
        print(f"Deleting {t}")
        shutil.rmtree(t)
    # shutil.rmtree(os.path.join(args.out, output + ".zarr"))
