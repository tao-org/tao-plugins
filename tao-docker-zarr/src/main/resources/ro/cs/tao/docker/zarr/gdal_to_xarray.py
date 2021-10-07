#!/usr/bin/env python

import argparse
from datetime import datetime
import numpy as np
from osgeo import gdal
import os
import pandas as pd
import pyproj
import re
import shutil
from typing import Dict, Any, Tuple, List, Union
import xarray as xr

DEBUG = False
SENTINEL_1_BACKSCATTER = "SENTINEL_1_BACKSCATTER"
SENTINEL_1_COHERENCE = "SENTINEL_1_COHERENCE"
SENTINEL_2_LAI = "SENTINEL_2_LAI"


DEFAULT_METADATA = dict(
    Conventions="CF-1.7",
    title='Agricultural Virtual Laboratory Dataset',
    summary='This dataset is used to demonstrate the AVL'
            ' common dataset convention',
    keywords='ESA, AVL, Agriculture, EO'
)


class Dataset(object):
    def __init__(self, product):
        self.product: Product = product
        self.inverse_y: bool = False
        self.time_name: str = 'time'
        self.time_units: str = 'seconds since 2000-01-01T00:00:00'
        self.time_calendar: str = 'proleptic_gregorian'
        self.time_periods: int = 1
        self.time_start: str = product.date
        self.variables: List[Tuple[str, str, Dict[str, Any]]] = None
        self.metadata: Dict[str, Any] = None


    def get_time_coordinate(self):
        time_var = xr.DataArray([self.time_start], dims=self.time_name)
        time_var.encoding['units'] = self.time_units
        time_var.encoding['calendar'] = self.time_calendar
        time_coordinate = {self.time_name : time_var}
        return time_coordinate


    def get_metadata(self):
        attrs = dict(DEFAULT_METADATA)
        if self.metadata:
            attrs.update(self.metadata)
        attrs.update(self.product.get_geospatial_attrs())
        attrs.update(self.get_time_coverage_attrs())
        return attrs


    def get_data_vars(self):
        data_vars = dict()
        x_name, y_name = self.product.xy_names
        height, width = self.product.xy_size
        dims = (self.time_name, y_name, x_name)
        shape = (self.time_periods, height, width)
        if DEBUG:
            print(f"Data vars dimensions: {dims}")
            print(f"Data vars shape: {shape}")
        self.variables = self.product.get_variables(shape)
        if self.variables:
            for var_name, _, var_value, attrs in self.variables:
                if self.product.crs is not None:
                    attrs['grid_mapping'] = 'crs'
                data_vars[var_name] = xr.DataArray(
                    var_value,
                    dims=dims,
                    attrs=attrs
                )
        crs = self.product.crs
        if crs is not None:
            data_vars['crs'] = xr.DataArray(0, attrs=crs.to_cf()).astype(self.product.crs_dtype)
        return data_vars


    def get_time_coverage_attrs(self):
        return dict(
            time_coverage_start=str(self.time_start),
        )


    def get_dataset(self):
        data_vars = self.get_data_vars()
        coords = dict()
        time_coords = self.get_time_coordinate()
        xy_coords = self.product.get_xy_coordinates()
        coords.update(time_coords)
        coords.update(xy_coords)
        attrs = self.get_metadata()

        dataset = xr.Dataset(
            data_vars=data_vars,
            coords=coords,
            attrs=attrs
        )

        y_name, x_name = self.product.xy_names
        chunks = {
            y_name: 'auto',
            x_name: 'auto',
            'time': 1,
        }
        if self.product.xy_tile_size is not None:
            x_tile_size, y_tile_size = self.product.xy_tile_size
            chunks.update({
                y_name: y_tile_size,
                x_name: x_tile_size,
            })

        return dataset.chunk(chunks=chunks)


    def write_dataset(self, file_path: str):
        dataset = self.get_dataset()
        if DEBUG:
            print(f'Writing {file_path}...')
            print(dataset)
        dataset.to_zarr(file_path)
        shutil.make_archive(file_path, "zip", file_path)


class Product(object):
    def __init__(self, input, type, date):
        self.input = input
        self.type = type
        self.date = date
        # Default values
        self.xy_tile_size: Union[int, Tuple[int, int]] = (1024, 1024)
        self.xy_dtype: np.dtype = 'float32'
        self.crs_dtype: np.dtype = 'int32'
        self.crs: Union[str, pyproj.CRS] = None

    def _get_axis_info(self):
        cs_to_cf = self.crs.cs_to_cf()
        items = {}
        for item in cs_to_cf:
            axis = Axis(item)
            items[axis.axis] = axis
        if DEBUG:
            print(f"Axis information: {items}")
        return items

    def get_variables(self, shape):
        band_values = self.raster.GetRasterBand(1).ReadAsArray()
        # print(band_values.dtype)
        band_reshaped = band_values.reshape(shape)
        variables = [
            (
                'band_1',
                'float32',
                band_reshaped,
                dict(
                    long_name='Band 1',
                    #...
                )
            ),
        ]
        if DEBUG:
            print(f"Variables: {variables}")
        return variables

    def get_xy_coordinates(self):
        width, height = self.xy_size

        x_start, y_start = self.xy_start
        x_res, y_res = self.xy_res

        (x_name, y_name) = (self.axis['X'].name, self.axis['Y'].name)
        (x_standard_name, y_standard_name) = (self.axis['X'].standard_name, self.axis['Y'].standard_name)
        (x_long_name, y_long_name) = (self.axis['X'].long_name, self.axis['Y'].long_name)
        (x_units, y_units) = (self.axis['X'].unit, self.axis['Y'].unit)

        x_end = x_start + width * x_res
        y_end = y_start + height * y_res

        x_res_05 = 0.5 * x_res
        y_res_05 = 0.5 * y_res

        x_data = np.linspace(x_start + x_res_05, x_end - x_res_05, width, dtype=self.xy_dtype)
        y_data = np.linspace(y_start + y_res_05, y_end - y_res_05, height, dtype=self.xy_dtype)

        x_var = xr.DataArray(x_data, dims=x_name, attrs=dict(units=x_units))
        y_var = xr.DataArray(y_data, dims=y_name, attrs=dict(units=y_units))

        x_var.attrs.update(long_name=x_long_name, standard_name=x_standard_name)
        y_var.attrs.update(long_name=y_long_name, standard_name=y_standard_name)

        xy_coordinates = {y_name : y_var, x_name : x_var}
        if DEBUG:
            print(f"XY Coordinates {xy_coordinates}")
        return xy_coordinates

    def get_geospatial_attrs(self):
        width, height = self.xy_size
        x_start, y_start = self.xy_start
        x_res, y_res = self.xy_res

        x_end = x_start + width * x_res
        y_end = y_start + height * y_res

        x_units, y_units = self.xy_units

        attrs = dict(
            geospatial_bounds_crs=self.crs.to_wkt(),
            geospatial_bounds=f'POLYGON(('
                            f'{x_start} {y_start}, '
                            f'{x_start} {y_end}, '
                            f'{x_end} {y_end}, '
                            f'{x_end} {y_start}, '
                            f'{x_start} {y_start}'
                            f'))',
        )
        if self.crs.is_geographic:
            attrs.update(
                geospatial_lon_units=x_units,
                geospatial_lon_min=x_start,
                geospatial_lon_max=x_end,
                geospatial_lon_resolution=x_res,
                geospatial_lat_units=y_units,
                geospatial_lat_min=y_start,
                geospatial_lat_max=y_end,
                geospatial_lat_resolution=y_res,
            )
        else:
            attrs.update(
                geospatial_x_units=x_units,
                geospatial_x_min=x_start,
                geospatial_x_max=x_end,
                geospatial_x_resolution=x_res,
                geospatial_y_units=y_units,
                geospatial_y_min=y_start,
                geospatial_y_max=y_end,
                geospatial_y_resolution=y_res,
            )
        if DEBUG:
            print(f"Geospatial attributes: {attrs}")
        return attrs


class Axis(object):
    def __init__(self, axis):
        self.axis = axis['axis']
        self.unit = axis['units']
        self.standard_name = axis['standard_name']
        self.long_name = axis['long_name']
        if axis['standard_name'] in ['longitude', 'latitude']:
            self.name = axis['standard_name'][:3]
        else:
            self.name = axis['axis'].lower()

class Sentinel2Product(Product):
    def __init__(self, input, type, date):
        super().__init__(input, type, date)
        self.raster = gdal.Open(self.input, gdal.GA_ReadOnly)
        self.crs = pyproj.crs.CRS.from_wkt(self.raster.GetProjectionRef())
        self.axis = self._get_axis_info()
        self.xy_size = (self.raster.RasterXSize, self.raster.RasterYSize)
        geotransform = self.raster.GetGeoTransform()
        self.xy_res = (geotransform[1], geotransform[5])
        self.xy_start = (geotransform[0], geotransform[3])
        self.xy_names = (self.axis['X'].name, self.axis['Y'].name)
        self.xy_units = (self.axis['X'].unit, self.axis['Y'].unit)

    @staticmethod
    def get_S2_product(path):
        filename = os.path.basename(path)
        # '2010-01-01T00:00:00'
        product_search = re.search(".*_A(\d{8})T(\d{6})_\w.*", filename)
        if product_search:
            date_product = datetime.strptime(product_search.group(1), "%Y%m%d").strftime("%Y-%m-%d")
            time_product = datetime.strptime(product_search.group(2), "%H%M%S").strftime("%H:%M:%S")
            date_string = date_product + 'T' + time_product
            date = datetime.strptime(date_string, "%Y-%m-%dT%H:%M:%S")
            if DEBUG:
                print(f"Found date {date} in {filename}")
        if re.search("^S2[A-Z]*_L3(A|B)_\w.*", filename):
            return Sentinel2Product(path, SENTINEL_2_LAI, date)
        return None



class Sentinel1Product(Product):
    def __init__(self, input, type, date):
        super().__init__(input, type, date)
        self.raster = gdal.Open(self.input, gdal.GA_ReadOnly)
        self.crs = pyproj.crs.CRS.from_wkt(self.raster.GetProjectionRef())
        self.axis = self._get_axis_info()
        self.xy_size = (self.raster.RasterXSize, self.raster.RasterYSize)
        geotransform = self.raster.GetGeoTransform()
        self.xy_res = (geotransform[1], geotransform[5])
        self.xy_start = (geotransform[0], geotransform[3])
        self.xy_names = (self.axis['X'].name, self.axis['Y'].name)
        self.xy_units = (self.axis['X'].unit, self.axis['Y'].unit)


    @staticmethod
    def get_S1_product(path):
        filename = os.path.basename(path)
        # '2010-01-01T00:00:00'
        product_search = re.search(".*_(\d{8})T(\d{6})_\w.*", filename)
        if product_search:
            date_product = datetime.strptime(product_search.group(1), "%Y%m%d").strftime("%Y-%m-%d")
            time_product = datetime.strptime(product_search.group(2), "%H%M%S").strftime("%H:%M:%S")
            date_string = date_product + 'T' + time_product
            date = datetime.strptime(date_string, "%Y-%m-%dT%H:%M:%S")
            if DEBUG:
                print(f"Found date {date} in {filename}")
        if re.search("^S1[A-Z]_L2_BCK_\w.*", filename):
            return Sentinel1Product(path, SENTINEL_1_BACKSCATTER, date)
        if re.search("^S1[A-Z]_L2_COH_\w.*", filename):
            return Sentinel1Product(path, SENTINEL_1_COHERENCE, date)
        return None


parser = argparse.ArgumentParser(description="Launcher GDAL to Zarr convertor")
parser.add_argument('-input', required=True)
parser.add_argument('-out', required=True)
parser.add_argument('-name', required=True)
args = parser.parse_args()

# Parameters checking
input = args.input
output = os.path.join(args.out, args.name) + '.zarr'

product = Sentinel1Product.get_S1_product(input) or Sentinel2Product.get_S2_product(input)
if DEBUG:
    print(f"Identified product of type {type(product)}")
dataset = Dataset(product)
dataset.write_dataset(output)
