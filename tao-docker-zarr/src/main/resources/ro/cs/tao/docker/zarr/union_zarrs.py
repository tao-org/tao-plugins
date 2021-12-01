#!/usr/bin/env python

import argparse
import os
import xarray as xr
from datetime import datetime

EPOCH = "seconds since 2000-01-01T00:00:00"

start = datetime.now()
print(f"{datetime.now()} Started")

parser = argparse.ArgumentParser(description="Union of multiple Zarr products")
parser.add_argument('--inputs', required=True, action='store', type=str, nargs="+")
parser.add_argument('--out', required=True)
parser.add_argument('--band-name', required=True)
args = parser.parse_args()

class Union(object):
    def __init__(self, products, out, band_name):
        self.products = products
        self.output = out
        self.band_name = band_name
        self.concatenation = None
        self.files = []

    def union(self):
        if len(self.products) == 0:
            print("Cannot concatenate 0 products")
            return -1
        with xr.open_zarr(self.products[0]) as ds:
            self.concatenation = ds
        self.files.append(os.path.basename(self.products[0]))
        for i in range(1, len(self.products)):
            with xr.open_zarr(self.products[i]) as ds1:
                self.concatenation = xr.concat([self.concatenation, ds1], dim='time', data_vars=[self.band_name], coords=['time'])
            self.files.append(os.path.basename(self.products[i]))

    def write(self):
        self.concatenation.attrs['sources'] = self.files
        self.concatenation.time.encoding['units'] = EPOCH
        self.concatenation.to_zarr(self.output)
        # shutil.make_archive(self.output, "zip", self.output)


union = Union(args.inputs, args.out, args.band_name)
if not os.path.exists(args.out):
    os.mkdir(args.out)

union.union()
union.write()

end = datetime.now()
print(f"{datetime.now()} Done")
print(f"Done in {end - start}")

