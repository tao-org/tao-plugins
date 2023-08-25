#!/usr/bin/env python

import argparse
import datetime
import os
import subprocess

start_time = datetime.datetime.now()

DEFAULT_CONVERTOR_IMAGE = "gdal_to_xarray"
DEFAULT_SCRIPT_NAME = "union_zarrs.py"
DEBUG = True

parser = argparse.ArgumentParser(description="Launcher convertor")
parser.add_argument('--inputs', required=True, action='store', type=str, nargs="+")
parser.add_argument('--out', required=False, default="/home/lidia/result_union.zarr")
parser.add_argument('--band-name', required=True)
args = parser.parse_args()

def union(inputs):
    cmd = []
    # Docker command
    cmd.append("docker")
    cmd.append("run")
    cmd.append("--rm")
    cmd.append("-u")
    cmd.append("{}:{}".format(os.getuid(), os.getgid()))

    # Mount the path to the script
    cmd.append("-v")
    cmd.append("{}:{}".format(os.path.abspath(DEFAULT_SCRIPT_NAME), os.path.join("/usr/bin", DEFAULT_SCRIPT_NAME)))

    # Mount the path to the S1 products
    for product in inputs:
        cmd.append("-v")
        cmd.append("{}:{}".format(os.path.abspath(product), os.path.abspath(product)))

    # Mount the path to the output directory
    cmd.append("-v")
    out_dir = os.path.abspath(os.path.join(args.out, os.pardir))
    cmd.append("{}:{}".format(out_dir, out_dir))

    cmd.append(DEFAULT_CONVERTOR_IMAGE)

    # Actual command
    cmd.append(DEFAULT_SCRIPT_NAME)
    cmd.append("--inputs")
    for product in inputs:
        cmd.append(product)
    cmd.append("--out")
    cmd.append(args.out)
    cmd.append("--band-name")
    cmd.append(args.band_name)

    try:
        if DEBUG:
            str_cmd = '\n(info) Running cmd: '
            for field in cmd:
                str_cmd += field + " "
            print(str_cmd)
        ret_val = subprocess.call(cmd, shell=False)
    except Exception as e:
        print("Exception encountered {}.".format(e))



union(args.inputs)
end_time = datetime.datetime.now()
print(" Finished converting in: {}".format(str(end_time - start_time)))

