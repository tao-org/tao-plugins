/*
 * Copyright (C) 2018 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package ro.cs.tao.integration.geostorm;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.geojson.geom.GeometryJSON;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import ro.cs.tao.integration.geostorm.model.RasterProduct;
import ro.cs.tao.integration.geostorm.model.Resource;

import java.io.IOException;
import java.io.StringReader;

/**
 * Tests for GeostormClient
 */
@RunWith(SpringRunner.class)
@ContextConfiguration("classpath:geostorm-client-context.xml")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GeostormClientTest {

    private static Log log = LogFactory.getLog(GeostormClientTest.class);

    @Autowired
    private GeostormClient geostormClient;

    @Test
    public void T_01_addResource()
    {
        log.info("test addResource....");

        Resource resource = new Resource();

        // set first the mandatory fields
        resource.setExecution_id(0);
        resource.setData_path("//myPath");
        resource.setData_type("output");
        resource.setName("S2_Dummy_Product");
        resource.setShort_description("Sentinel 2 Granule S2_Dummy_Product");
        resource.setOrganisation("CS");
        resource.setUsage("Free");

        resource.setEntry_point("aaa");
        resource.setResource_storage_type("process_result");

        resource.setRelease_date("2018-03-08");
        resource.setCollection("Sentinel_2");
        /*resource.setWkb_geometry("{\"type\": \"Polygon\",\n" +
          "            \"coordinates\": [[[143.503571067188, 44.2259751275381],\n" +
          "                             [143.462735477681, 43.2382731563042],\n" +
          "                             [144.812631659451, 43.2012113546965],\n" +
          "                             [144.875738946256, 44.1876215835231],\n" +
          "                             [143.503571067188, 44.2259751275381]]]}");*/

        // works (the field is optional)
        //resource.setWkb_geometry(null);

        GeometryJSON gtjson = new GeometryJSON();
        Polygon polygon = null;
        try {
            polygon = gtjson.readPolygon(new StringReader("{\"type\": \"Polygon\",\n" +
              "            \"coordinates\": [[[143.503571067188, 44.2259751275381],\n" +
              "                             [143.462735477681, 43.2382731563042],\n" +
              "                             [144.812631659451, 43.2012113546965],\n" +
              "                             [144.875738946256, 44.1876215835231],\n" +
              "                             [143.503571067188, 44.2259751275381]]]}"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        resource.setWkb_geometry("{\"type\": \"Polygon\",\n" +
          "            \"coordinates\": [[[143.503571067188, 44.2259751275381],\n" +
          "                             [143.462735477681, 43.2382731563042],\n" +
          "                             [144.812631659451, 43.2012113546965],\n" +
          "                             [144.875738946256, 44.1876215835231],\n" +
          "                             [143.503571067188, 44.2259751275381]]]}");


        Integer[] coord_sys = {4326};
        resource.setCoord_sys(coord_sys);

        geostormClient.addResource(resource);
    }

    @Test
    public void T_02_getResources()
    {
        log.info("test getResources ...");

        log.info(geostormClient.getResources());
    }

    @Test
    public void T_03_Raster_import()
    {
        log.info("test raster import....");

        RasterProduct rasterProduct = new RasterProduct();

        rasterProduct.setProduct_path("/imgdata/S2A_MSIL1C_20180101T091351_N0206_R050_T35TLJ_20180101T113355.SAFE");
        rasterProduct.setOwner("admin");
        rasterProduct.setCollection("Sentinel_1");
        rasterProduct.setSite("No idea");
        rasterProduct.setMosaic_name("Mosaic_S2A");
        rasterProduct.setEntry_point(new String[]{"MTD_MSIL1C.xml"});
        rasterProduct.setProduct_date("2018-05-16");
        rasterProduct.setExtent("{\"type\": \"Polygon\",\n" +
          "            \"coordinates\": [[[63.7454106, 2.1718301],\n" +
          "                             [63.7719564, 2.9663673],\n" +
          "                             [62.9776581, 2.9970139],\n" +
          "                             [62.9513407, 2.1942589],\n" +
          "                             [63.7454106, 2.1718301]]]}");

        rasterProduct.setOrganization("CS");

        geostormClient.importRaster(rasterProduct);
    }

    @Test
    public void T_04_Raster_import()
    {
        log.info("test raster import TIFF ....");

        RasterProduct rasterProduct = new RasterProduct();

        rasterProduct.setProduct_path("/imgdata/2-output_snap-ndvi");
        rasterProduct.setOwner("admin");
        rasterProduct.setCollection("Sentinel_1");
        rasterProduct.setSite("No idea");
        rasterProduct.setMosaic_name("Mosaic_snap_ndvi");
        rasterProduct.setEntry_point(new String[]{"2-output_snap-ndvi.tif"});
        rasterProduct.setProduct_date("2018-05-16");
        rasterProduct.setExtent("{\"type\": \"Polygon\",\n" +
          "            \"coordinates\": [[[143.503571067188, 44.2259751275381],\n" +
          "                             [143.462735477681, 43.2382731563042],\n" +
          "                             [144.812631659451, 43.2012113546965],\n" +
          "                             [144.875738946256, 44.1876215835231],\n" +
          "                             [143.503571067188, 44.2259751275381]]]}");

        rasterProduct.setOrganization("CS");

        geostormClient.importRaster(rasterProduct);
    }

    @Test
    public void T_05_Raster_import()
    {
        log.info("test raster import TIFF ....");

        RasterProduct rasterProduct = new RasterProduct();

        rasterProduct.setProduct_path("/imgdata/12-output_snap-ndvi");
        rasterProduct.setOwner("admin");
        rasterProduct.setCollection("Sentinel_1");
        rasterProduct.setSite("No idea");
        rasterProduct.setMosaic_name("Mosaic_snap_ndvi_new");
        rasterProduct.setEntry_point(new String[]{"8-output_snap-ndvi.tif"});
        rasterProduct.setProduct_date("2018-05-17");

        rasterProduct.setExtent("{\"type\": \"Polygon\",\n" +
          "            \"coordinates\": [[[24.4959286, 44.2259642],\n" +
          "                             [24.5367723, 43.2382626],\n" +
          "                             [25.888683, 43.2593919],\n" +
          "                             [25.8702373, 44.2478307],\n" +
          "                             [24.4959286, 44.2259642]]]}");

        rasterProduct.setOrganization("CS");

        geostormClient.importRaster(rasterProduct);
    }

    @Test
    public void T_06_Raster_import()
    {
        log.info("test raster import TIFF ....");

        RasterProduct rasterProduct = new RasterProduct();

        rasterProduct.setProduct_path("/imgdata/13-output_otbcli_RigidTransformResample");
        rasterProduct.setOwner("admin");
        rasterProduct.setCollection("Sentinel_1");
        rasterProduct.setSite("No idea");
        rasterProduct.setMosaic_name("Mosaic_otbcli_new");
        rasterProduct.setEntry_point(new String[]{"9-output_otbcli_RigidTransformResample.tif"});
        rasterProduct.setProduct_date("2018-05-17");

        rasterProduct.setExtent("{\"type\": \"Polygon\",\n" +
          "            \"coordinates\": [[[24.4959286, 44.2259642],\n" +
          "                             [24.5367723, 43.2382626],\n" +
          "                             [25.888683, 43.2593919],\n" +
          "                             [25.8702373, 44.2478307],\n" +
          "                             [24.4959286, 44.2259642]]]}");


        rasterProduct.setOrganization("CS");

        geostormClient.importRaster(rasterProduct);
    }

    @Test
    public void T_07_Raster_import()
    {
        log.info("test raster import TIFF ....");

        RasterProduct rasterProduct = new RasterProduct();

        rasterProduct.setProduct_path("/imgdata/1-2-output_snap-ndvi");
        rasterProduct.setOwner("admin");
        rasterProduct.setCollection("GeoTIFF");
        rasterProduct.setSite("No idea");
        rasterProduct.setMosaic_name("Mosaic_geotiff");
        rasterProduct.setEntry_point(new String[]{"1-2-output_snap-ndvi.tif"});
        rasterProduct.setProduct_date("2018-05-21");

        rasterProduct.setExtent("{\"type\": \"Polygon\",\n" +
          "            \"coordinates\": [[[24.4959286, 44.2259642],\n" +
          "                             [24.5367723, 43.2382626],\n" +
          "                             [25.888683, 43.2593919],\n" +
          "                             [25.8702373, 44.2478307],\n" +
          "                             [24.4959286, 44.2259642]]]}");


        rasterProduct.setOrganization("CS");

        if(geostormClient.canCreateCollectionMapFileIfNotExists(rasterProduct.getCollection())){
            geostormClient.importRaster(rasterProduct);
        }
        else{
            log.error("Cannot create collection map file " + rasterProduct.getCollection());
        }
    }

}
