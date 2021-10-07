#!/usr/bin/env python

import argparse
import xarray as xr
from datetime import datetime
import os
import shutil

EPOCH = "seconds since 2000-01-01T00:00:00"
EXTENSION = '.zarr'

start = datetime.now()
print(f"{datetime.now()} Started")

parser = argparse.ArgumentParser(description="Union of multiple Zarr products")
parser.add_argument('-inputs', required=True, action='store', type=str, nargs="+")
parser.add_argument('-out', required=False, default="/home/lidia/out")
parser.add_argument('-name', required=False, default="result_union")
args = parser.parse_args()

class Union(object):
    def __init__(self, products, out, name):
        self.products = products
        self.output = os.path.join(out, name) + EXTENSION
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
                self.concatenation = xr.concat([self.concatenation, ds1], dim='time', data_vars=['band_1'], coords=['time'])
            self.files.append(os.path.basename(self.products[i]))


    def write(self):
        self.concatenation.attrs['sources'] = self.files
        self.concatenation.time.encoding['units'] = EPOCH
        self.concatenation.to_zarr(self.output, mode='w')
        print(self.concatenation)
        shutil.make_archive(self.output, "zip", self.output)


union = Union(args.inputs, args.out, args.name)
union.union()
union.write()

end = datetime.now()
print(f"{datetime.now()} Done")
print(f"Done in {end - start}")


