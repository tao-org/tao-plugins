#!/usr/bin/env python3

import argparse
import numpy as np
import os
import pyproj
import xarray as xr
from datetime import datetime
from osgeo import gdal, osr
from typing import Dict, Any, Tuple, List, Union

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
        return {self.time_name : time_var}


    def get_metadata(self):
        attrs = dict(DEFAULT_METADATA)
        if self.metadata:
            attrs.update(self.metadata)
        attrs.update(self.product.get_geospatial_attrs())
        attrs.update(self.get_time_coverage_attrs())
        return attrs


    def get_time_coverage_attrs(self):
        return dict(
            time_coverage_start=str(self.time_start),
        )


    def get_dataset(self):
        data_vars = self.product.get_variables(self.time_name, self.time_periods)
        coords = {}
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
            'time': 1,
            y_name: 'auto',
            x_name: 'auto',
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
        dataset.to_zarr(file_path)
        # shutil.make_archive(file_path, "zip", file_path)
        # shutil.rmtree(file_path)


class Product(object):
    def __init__(self, input, band_names, date, fill, scale, offset, units):
        self.input = input
        self.fill = fill
        self.band_names = band_names
        self.date = date
        self.scale = scale
        self.offset = offset
        self.units = units

        # Raster info
        self.raster = gdal.Open(self.input, gdal.GA_ReadOnly)
        self.crs = pyproj.crs.CRS.from_wkt(self.raster.GetProjectionRef())
        self.axis = self._get_axis_info()
        self.xy_size = (self.raster.RasterXSize, self.raster.RasterYSize)
        geotransform = self.raster.GetGeoTransform()
        self.xy_res = (geotransform[1], geotransform[5])
        self.xy_start = (geotransform[0], geotransform[3])
        self.xy_names = (self.axis['X'].name, self.axis['Y'].name)
        self.xy_units = (self.axis['X'].unit, self.axis['Y'].unit)

        # Default values
        self.xy_tile_size: Union[int, Tuple[int, int]] = (1024, 1024)
        self.xy_dtype: np.dtype = 'float32'
        self.crs_dtype: np.dtype = 'int32'

    def _get_axis_info(self):
        cs_to_cf = self.crs.cs_to_cf()
        items = {}
        for item in cs_to_cf:
            axis = Axis(item)
            items[axis.axis] = axis
        return items

    def get_variables(self, time_name, time_periods):
        data_vars = {}
        x_name, y_name = self.xy_names
        width, height = self.xy_size
        dims = (time_name, y_name, x_name)
        shape = (time_periods, height, width)

        crs = self.crs
        if crs is not None:
            data_vars['crs'] = xr.DataArray(0, attrs=crs.to_cf()).astype(self.crs_dtype)

        nb_bands = self.raster.RasterCount

        if not self.band_names:
            band_names = ["_".join(["band", str(i)]) for i in range(1, nb_bands+1)]
        else:
            band_names = self.band_names

        for band_index in range(1, len(band_names)+1):
            raster_band = self.raster.GetRasterBand(band_index)
            band_name = band_names[band_index-1].lower()
            band_values = raster_band.ReadAsArray()
            band_reshaped = band_values.reshape(shape)

            var_attributes = {
                "long_name": band_name,
                "standard_name": band_name,
                "units": str(self.units)
            }

            fill = self.fill or raster_band.GetNoDataValue()
            if fill is not None:
                var_attributes["_FillValue"] = float(fill)
            if self.scale is not None:
                band_values = band_values
                var_attributes["scale_factor"] = 1/float(self.scale)
            if self.offset is not None:
                var_attributes["add_offset"] = float(self.offset)

            if crs is not None:
                var_attributes['grid_mapping'] = 'crs'

            data_vars[band_name] = xr.DataArray(band_reshaped, dims=dims, attrs=var_attributes)

        return data_vars

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

        return {y_name : y_var, x_name : x_var}

    def _reproj_coords(self, coords, src_srs, tgt_srs):
        trans_coords = []
        transform = osr.CoordinateTransformation(src_srs, tgt_srs)
        for x, y in coords:
            x, y, z = transform.TransformPoint(x, y)
            trans_coords.append([x, y])
        return trans_coords

    def _get_extent(self, gt, cols, rows):
        ext = []
        x_arr = [0, cols]
        y_arr = [0, rows]

        for px in x_arr:
            for py in y_arr:
                x = gt[0] + px * gt[1] + py * gt[2]
                y = gt[3] + px * gt[4] + py * gt[5]
                ext.append([x, y])
            y_arr.reverse()
        return ext

    def get_footprint(self):
        (size_x, size_y) = self.xy_size
        geo_transform = self.raster.GetGeoTransform()
        extent = self._get_extent(geo_transform, size_x, size_y)

        source_srs = osr.SpatialReference()
        source_srs.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER)
        source_srs.ImportFromWkt(self.raster.GetProjection())

        crs = source_srs.ExportToWkt()

        target_srs = osr.SpatialReference()
        target_srs.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER)
        target_srs.ImportFromEPSG(4326)

        wgs84_extent = self._reproj_coords(extent, source_srs, target_srs)
        return (np.array(wgs84_extent), np.array(extent), crs)

    def get_geospatial_attrs(self):
        width, height = self.xy_size
        x_start, y_start = self.xy_start
        x_res, y_res = self.xy_res

        x_end = x_start + width * x_res
        y_end = y_start + height * y_res

        x_units, y_units = self.xy_units

        attrs = dict(
            geospatial_bounds_crs=self.crs.to_wkt(pyproj.enums.WktVersion.WKT1_GDAL),
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
            (wgs84_extent, _, _) = self.get_footprint()
            longitude = wgs84_extent[:, 0]
            latitude = wgs84_extent[:, 1]
            # Latitude/Longitude attributes
            attrs.update(
                geospatial_lon_units="degrees_east",
                geospatial_lon_max=longitude.max(),
                geospatial_lon_min=longitude.min(),
                geospatial_lat_units="degrees_north",
                geospatial_lat_max=latitude.max(),
                geospatial_lat_min=latitude.min(),
            )
            # X/Y attributes
            attrs.update(
                geospatial_x_units=x_units,
                geospatial_x_max=x_end,
                geospatial_x_min=x_start,
                geospatial_x_resolution=x_res,
                geospatial_y_units=y_units,
                geospatial_y_max=y_end,
                geospatial_y_min=y_start,
                geospatial_y_resolution=y_res,
            )
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


parser = argparse.ArgumentParser(description="Launcher GDAL to Zarr convertor")
parser.add_argument('--input', required=True)
parser.add_argument('--out', required=True)
parser.add_argument('--band-names', required=False, type=str, nargs="+")
parser.add_argument('--date', required=True)
parser.add_argument('--fill', required=False)
parser.add_argument('--scale', required=False)
parser.add_argument('--offset', required=False)
parser.add_argument('--units', required=False, default=1)
args = parser.parse_args()

# Parameters checking
input = args.input
output = args.out
date = args.date
# band_name = args.band_name
band_names = args.band_names
scale = args.scale
fill = args.fill
offset = args.offset
units = args.units

filename = os.path.basename(input)
(name, ext) = os.path.splitext(filename)

product_date = datetime.strptime(date, "%Y-%m-%dT%H:%M:%S")

# if os.path.exists(output):
#     if os.path.isdir(output):
#         output_name = name + '.zarr'
#         output_path = os.path.join(output, output_name)
#     else:
#         (output_name, ext) = os.path.splitext(output)
#         if ext.lower() != '.zarr':
#             raise Exception(f"The output {output} must be zarr compatible.")
#         else:
#             output_path = output
# else:
#     # Create the parent directory
#     os.makedirs(os.path.dirname(output), exist_ok=True)
#     (output_name, ext) = os.path.splitext(output)
#     if not ext

product = Product(input, band_names, product_date, fill, scale, offset, units)
dataset = Dataset(product)
dataset.write_dataset(output)
