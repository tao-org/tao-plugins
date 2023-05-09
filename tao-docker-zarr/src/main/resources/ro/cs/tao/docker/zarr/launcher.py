#!/usr/bin/env python

import argparse
import os
import subprocess
from datetime import datetime

DATE_FORMAT = "%Y-%m-%d"
start_time = datetime.now()

DEFAULT_CONVERTOR_IMAGE = "gdal_to_xarray"
DEFAULT_SCRIPT_NAME = "gdal_to_xarray.py"
DEBUG = True

parser = argparse.ArgumentParser(description="Launcher convertor")
parser.add_argument('--input', required=True)
parser.add_argument('--out', required=False, default="/home/lidia/test.zarr")
parser.add_argument('--date', required=True)
parser.add_argument('--band-name', required=False)
parser.add_argument('--fill', required=False)
parser.add_argument('--scale', required=False)
parser.add_argument('--offset', required=False)
args = parser.parse_args()

def convert_to_xarray(produs_l1, output_path):
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

    # Mount the path to the S1 product
    cmd.append("-v")
    cmd.append("{}:{}".format(os.path.abspath(produs_l1), os.path.abspath(produs_l1)))

    # Mount the path to the output directory
    cmd.append("-v")
    cmd.append("{}:{}".format(output_path, output_path))


    cmd.append(DEFAULT_CONVERTOR_IMAGE)

    # Actual command
    cmd.append(DEFAULT_SCRIPT_NAME)
    cmd.append("--input")
    cmd.append(produs_l1)
    cmd.append("--out")
    cmd.append(args.out)
    cmd.append("--date")
    cmd.append(args.date)
    if args.band_name:
        cmd.append("--band-name")
        cmd.append(args.band_name)
    if args.scale:
        cmd.append("--scale")
        cmd.append(args.scale)
    if args.fill:
        cmd.append("--fill")
        cmd.append(args.fill)
    if args.offset:
        cmd.append("--offset")
        cmd.append(args.offset)

    try:
        if DEBUG:
            str_cmd = '\n(info) Running cmd: '
            for field in cmd:
                str_cmd += field + " "
            print(str_cmd)
        ret_val = subprocess.call(cmd, shell=False)
    except Exception as e:
        print("Exception encountered {}.".format(e))


input = args.input
output = args.out

output_path = os.path.dirname(output)

convert_to_xarray(input, output_path)
end_time = datetime.now()
print(" Finished converting in: {}".format(str(end_time - start_time)))

