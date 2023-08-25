#!/usr/bin/env python3

import argparse
import datetime
import glob
import joblib
import numpy as np
import os
import pandas as pd
import re
from osgeo import gdal
from osgeo import gdalconst

start_time = datetime.datetime.now()

NotAValue = -1
SCL_DARK_AREA_PIXELS = 2
SCL_VEGETATION_PIXELS = 4
SCL_NON_VEGETATATED_PIXELS =5
SCL_UNCLASSIFIED_PIXELS = 7

parser = argparse.ArgumentParser(description="buned area computation")
parser.add_argument('-l2a', required = True, action='store', type=str, nargs="+")
parser.add_argument('-model', required= False, default ="model_gnb_ba.pkl"),
parser.add_argument('-out', required = True, default = "/usr/share/ba/out")
parser.add_argument('-name', required = False)
parser.add_argument('-res', required = False, type = int, choices=[10,20], default = 10)
parser.add_argument('-final', required=False, default=False, action = "store_true")
parser.add_argument('-debug', required=False, default=False, action = "store_true")
args = parser.parse_args()

if len(args.l2a) < 2:
    print("Error: You must provide at least two L2A products.")
    os._exit(1)

if args.res == 10:
    TARGET_SIZE = 10980
if args.res == 20:
    TARGET_SIZE = 5490


class Band:
    def __init__(self, band_id, band_resolution):
        self.id = band_id
        self.resolution = band_resolution
        self.vals = None
    
    def get_band_values(self, l2a_path, is_band):
        if is_band:
            band_pattern = "GRANULE/L2A*/IMG_DATA/R{}m/*_B{}_{}m.jp2".format(self.resolution, self.id, self.resolution)
            buffer_type = gdal.GDT_Float32
            resample_algo = gdalconst.GRIORA_Cubic
        else:
            #masks
            band_pattern = "GRANULE/L2A*/IMG_DATA/R{}m/*_{}_{}m.jp2".format(self.resolution, self.id, self.resolution)
            buffer_type = gdal.GDT_Int16
            resample_algo = gdalconst.GRIORA_NearestNeighbour
        band_path = os.path.join(l2a_path, band_pattern)
        if args.debug:
            print("(DEBUG) Searching for {}".format(band_path))
        images = glob.glob(band_path)
        if len(images) == 1:
            if args.debug:
                print("(DEBUG) Found img {}.".format(images[0]))
            try:
                band_image = gdal.Open(images[0])
                band = band_image.GetRasterBand(1)
                if self.resolution == args.res:
                    self.vals = np.nan_to_num(band.ReadAsArray(xoff=0, yoff=0, buf_type=buffer_type))
                else:
                    self.vals = np.nan_to_num(band.ReadAsArray(xoff=0, yoff=0, buf_type=buffer_type, buf_xsize = TARGET_SIZE ,buf_ysize = TARGET_SIZE, resample_alg = resample_algo))
                if args.debug:
                    print("(DEBUG) Finished reading {}".format(images[0]))
            except Exception as e:
                print("(ERROR) Can NOT {} read due to: {}".format(images[0],e))
          
class L2A_Product:
    def __init__(self, l2a_path):
        self.b04 = Band('04', 10)
        self.b04.get_band_values(l2a_path, True)
        self.b08 = Band('08', 10)
        self.b08.get_band_values(l2a_path, True)
        self.b8A = Band('8A', 20)
        self.b8A.get_band_values(l2a_path, True)
        self.b11 = Band('11', 20)
        self.b11.get_band_values(l2a_path, True)
        self.b12 = Band('12', 20)
        self.b12.get_band_values(l2a_path, True)
        self.scl = Band('SCL', 20)
        self.scl.get_band_values(l2a_path, False)
        with np.errstate(divide='ignore', invalid='ignore'):
            self.nbr = (self.b8A.vals - self.b12.vals)/(self.b8A.vals + self.b12.vals)
            self.nbr2 = (self.b11.vals - self.b12.vals)/(self.b11.vals + self.b12.vals)
            self.bai = 1 /( (0.06 - (self.b08.vals/10000))**2 + (0.1 - (self.b04.vals/10000))**2 )
            self.mirbi = (self.b12.vals/1000) - (9.8 * self.b11.vals/10000) + 2 
            self.ndvi = (self.b08.vals - self.b04.vals)/(self.b08.vals + self.b04.vals)

class BA_Product:
    def __init__(self, pre_path, post_path, name):
        self.pre = L2A_Product(pre_path)
        self.post = L2A_Product(post_path)
        self.dnbr = self.pre.nbr - self.post.nbr
        self.dnbr2 = self.pre.nbr2 - self.post.nbr2
        self.dbai = self.post.bai - self.pre.bai
        self.dmirbi = self.post.mirbi - self.pre.mirbi
        self.dndvi = self.pre.ndvi - self.post.ndvi
        self.name = name
        
    def classify(self):
        if args.model:
            gnb = joblib.load(args.model)
        features_to_predict = pd.DataFrame()
        features_to_predict['dnbr'] = np.nan_to_num(self.dnbr.reshape(TARGET_SIZE*TARGET_SIZE))
        features_to_predict['dnbr2'] = np.nan_to_num(self.dnbr2.reshape(TARGET_SIZE*TARGET_SIZE))
        features_to_predict['dbai'] = np.nan_to_num(self.dbai.reshape(TARGET_SIZE*TARGET_SIZE))
        features_to_predict['dmirbi'] = np.nan_to_num(self.dmirbi.reshape(TARGET_SIZE*TARGET_SIZE))
        features_to_predict['dndvi'] = np.nan_to_num(self.dndvi.reshape(TARGET_SIZE*TARGET_SIZE))
        features_to_predict['post_8A'] = np.nan_to_num((self.post.b8A.vals/10000).reshape(TARGET_SIZE*TARGET_SIZE))
        #features_to_predict.to_csv(os.path.join(args.out,"features.csv"))
        burned_predicted = gnb.predict(features_to_predict).reshape((TARGET_SIZE,TARGET_SIZE))

        #apply scl masks from pre and post l2a products
        bm_pre = (self.pre.scl.vals == SCL_DARK_AREA_PIXELS) | \
                (self.pre.scl.vals == SCL_VEGETATION_PIXELS) | \
                (self.pre.scl.vals == SCL_NON_VEGETATATED_PIXELS) | \
                (self.pre.scl.vals == SCL_UNCLASSIFIED_PIXELS) 
        bm_post = (self.post.scl.vals == SCL_DARK_AREA_PIXELS) | \
                (self.post.scl.vals == SCL_VEGETATION_PIXELS) | \
                (self.post.scl.vals == SCL_NON_VEGETATATED_PIXELS) | \
                (self.post.scl.vals == SCL_UNCLASSIFIED_PIXELS) 
        
        burned_predicted = burned_predicted * bm_pre * bm_post

        #exporting raster image
        if args.res == 20:
            ref_img_pattern = "GRANULE/L2A*/IMG_DATA/R20m/*_B8A_20m.jp2"
        if args.res == 10:
            ref_img_pattern = "GRANULE/L2A*/IMG_DATA/R10m/*_B08_10m.jp2"
        ref_img_path = os.path.join(args.l2a[0], ref_img_pattern)
        ref_img = glob.glob(ref_img_path)[0]
        ref = gdal.Open(ref_img)
        fileformat = "GTiff"
        driver = gdal.GetDriverByName(fileformat)
        dst_ds = driver.Create(os.path.join(args.out,self.name), xsize=TARGET_SIZE, ysize=TARGET_SIZE, bands=1, eType=gdal.GDT_Byte, options=["COMPRESS=DEFLATE"])
        dst_ds.SetProjection(ref.GetProjection())
        dst_ds.SetGeoTransform(ref.GetGeoTransform())
        dst_ds.GetRasterBand(1).WriteArray(burned_predicted)
        gdal.SetConfigOption("COMPRESS_OVERVIEW", "DEFLATE")
        dst_ds.BuildOverviews("NEAR", [2,4,8,16,32])


class L2a():
    def __init__(self, path):
        self.path = os.path.abspath(path)
        self.name = os.path.basename(self.path)
        print(self.name)
        date_and_time =  re.findall(r"_\d{8}T\d{6}_", self.name)
        if date_and_time:
            self.acq_date = date_and_time[0].strip("_").split("T")[0]
        else:
            self.acq_date = None
        tile = re.findall(r"_T\w{5}_", self.name)
        if tile:
            self.tile = tile[0].strip("_")
        else:
            self.tile = None


def compute_ba_map(pre_l2a, post_l2a, name):
    if args.debug:
        print("(DEBUG) pre {} post {}".format(pre_l2a, post_l2a))
    ba_map = BA_Product(pre_l2a, post_l2a, name)
    ba_map.classify()

intermediary_maps = []
for i in range(1,len(args.l2a)):
    if not os.path.exists(args.l2a[i-1]):
        print("(ERROR) The L2A path does not exist: {}".format(args.l2a[i-1]))
        os._exit(1)
    if not os.path.exists(args.l2a[i]):
        print("(ERROR) The L2A path does not exist: {}".format(args.l2a[i]))
        os._exit(1)
    pre = L2a(args.l2a[i-1])
    post = L2a(args.l2a[i])
    if args.name:
        name = "BA_{}_{}_{}D{}_R{}m.tif".format(args.name,post.tile,pre.acq_date, post.acq_date, args.res)
    else:
        name = "BA_{}_{}D{}_R{}m.tif".format(post.tile,pre.acq_date, post.acq_date, args.res)
    intermediary_maps.append(name)
    compute_ba_map(pre.path, post.path, name)

if args.final:
    ba_array = np.empty((TARGET_SIZE, TARGET_SIZE), dtype = int)
    for map in intermediary_maps:
        map_path = os.path.join(args.out, map)
        ba_band = gdal.Open(map_path)
        raster = ba_band.GetRasterBand(1)
        current_array = raster.ReadAsArray(xoff=0, yoff=0)
        ba_array = ba_array + current_array

    boolean_ba_array = ba_array > 0
    
    if args.res == 20:
        ref_img_pattern = "GRANULE/L2A*/IMG_DATA/R20m/*_B8A_20m.jp2"
    if args.res == 10:
        ref_img_pattern = "GRANULE/L2A*/IMG_DATA/R10m/*_B08_10m.jp2"
    ref_img_path = os.path.join(args.l2a[-1], ref_img_pattern)
    ref_img = glob.glob(ref_img_path)[0]
    ref = gdal.Open(ref_img)
    fileformat = "GTiff"
    driver = gdal.GetDriverByName(fileformat)
    if args.name:
        name = "BA_{}_{}_R{}m_final.tif".format(args.name,post.tile, args.res)
    else:
        name = "BA_{}R{}m_final.tif".format(post.tile, args.res)

    dst_ds = driver.Create(os.path.join(args.out,name), xsize=TARGET_SIZE, ysize=TARGET_SIZE, bands=1, eType=gdal.GDT_Byte, options=["COMPRESS=DEFLATE"])
    dst_ds.SetProjection(ref.GetProjection())
    dst_ds.SetGeoTransform(ref.GetGeoTransform())
    dst_ds.GetRasterBand(1).WriteArray(boolean_ba_array)
    gdal.SetConfigOption("COMPRESS_OVERVIEW", "DEFLATE")
    dst_ds.BuildOverviews("NEAR", [2,4,8,16,32])
    
    compute_ba_map(pre.path, post.path, args.name + ".tif")

end_time = datetime.datetime.now()
print(" Finished computing burned area mask in: {}".format(str(end_time - start_time)))



