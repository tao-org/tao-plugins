package ro.cs.tao.stac;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ro.cs.tao.stac.core.STACClient;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.parser.STACParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test {

    public static void main(String[] args) throws Exception {
        //testParser();
        testClient();
        System.exit(0);
    }

    private static void testParser() throws IOException {
        STACParser parser = new STACParser();
        Catalog catalog = parser.parseCatalogResponse(testCatalog());
        assert(catalog != null);
        CollectionList collections = parser.parseCollectionsResponse(testCollections());
        assert (collections != null);
        Collection collection = parser.parseCollectionResponse(testCollection());
        assert(collection != null);
        ItemCollection itemCollection = parser.parseItemCollectionResponse(testItems());
        assert(itemCollection != null);
    }

    private static void testClient() throws IOException {
        STACClient client = new STACClient("https://earth-search.aws.element84.com/v1", null);
        final String collectionName = "sentinel-2-l2a";
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        Catalog catalog = client.getCatalog();
        assert (catalog != null);
        CollectionList collectionList = client.listCollections();
        assert (collectionList != null);
        ItemCollection itemCollection = client.listItems(collectionName);
        assert(itemCollection != null);
        Item item = client.getItem(collectionName, itemCollection.getFeatures().get(0).getId());
        assert (item != null);
        Map<String, Object> params = new HashMap<>();
        params.put("bbox", "20.2201924985,43.6884447292,29.62654341,48.2208812526");
        params.put("datetime", "2022-05-01T00:00:00Z/2022-05-02T23:59:59Z");
        ItemCollection results = client.search(collectionName, params);
        assert (results != null);
        List<Item> features = results.getFeatures();
        if (features != null && features.size() > 0) {
            Item it = features.get(0);
            Map<String, Asset> assets = it.getAssets();
            System.out.println(mapper.writer().writeValueAsString(it));
            ObjectWriter writer = mapper.writerFor(Asset.class);
            for (Map.Entry<String, Asset> entry : assets.entrySet()) {
                System.out.println(entry.getKey() + ": " + writer.writeValueAsString(entry.getValue()));
            }

            /*if (assets.size() > 1) {
                client.download(assets.values().iterator().next(), Paths.get("D:\\test"));
            } else {
                client.download(it, Paths.get("D:\\test"));
            }*/
        }
    }

    private static String testCatalog() {
        return "{\n" +
                "  \"stac_version\": \"1.0.0\",\n" +
                "  \"id\": \"DEAfrica_data\",\n" +
                "  \"title\": \"Digital Earth Africa\",\n" +
                "  \"description\": \"Configure stac endpoint information in your Explorer `settings.env.py` file\",\n" +
                "  \"type\": \"Catalog\",\n" +
                "  \"links\": [\n" +
                "    {\n" +
                "      \"title\": \"Collections\",\n" +
                "      \"description\": \"All product collections\",\n" +
                "      \"rel\": \"children\",\n" +
                "      \"type\": \"application/json\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"Arrivals\",\n" +
                "      \"description\": \"Most recently added items\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"type\": \"application/json\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/arrivals\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"Item Search\",\n" +
                "      \"rel\": \"search\",\n" +
                "      \"type\": \"application/json\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/search\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"rel\": \"self\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"alos_palsar_mosaic\",\n" +
                "      \"description\": \"ALOS/PALSAR and ALOS-2/PALSAR-2 annual mosaic tiles generated for use in the Data Cube - 25m pixel spacing, WGS84. These tiles are derived from the orignal JAXA mosaics with conversion to GeoTIFF.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/alos_palsar_mosaic\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"cci_landcover\",\n" +
                "      \"description\": \"ESA Climate Change Initiative Land Cover\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/cci_landcover\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"cgls_landcover\",\n" +
                "      \"description\": \"Copernicus Global Land Service, Land Use/Land Cover at 100 m\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/cgls_landcover\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"crop_mask\",\n" +
                "      \"description\": \"Annual cropland extent map produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/crop_mask\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"crop_mask_central\",\n" +
                "      \"description\": \"Annual cropland extent map for Central Africa produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/crop_mask_central\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"crop_mask_eastern\",\n" +
                "      \"description\": \"Annual cropland extent map for Eastern Africa produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/crop_mask_eastern\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"crop_mask_indian_ocean\",\n" +
                "      \"description\": \"Annual cropland extent map for Indian Ocean Africa produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/crop_mask_indian_ocean\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"crop_mask_northern\",\n" +
                "      \"description\": \"Annual cropland extent map for Northern Africa produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/crop_mask_northern\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"crop_mask_sahel\",\n" +
                "      \"description\": \"Annual cropland extent map for Sahel Africa produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/crop_mask_sahel\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"crop_mask_southeast\",\n" +
                "      \"description\": \"Annual cropland extent map for Southeast Africa produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/crop_mask_southeast\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"crop_mask_southern\",\n" +
                "      \"description\": \"Annual cropland extent map for Southern Africa produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/crop_mask_southern\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"crop_mask_western\",\n" +
                "      \"description\": \"Annual cropland extent map for Western Africa produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/crop_mask_western\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"dem_cop_30\",\n" +
                "      \"description\": \"Copernicus DEM 30 m\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/dem_cop_30\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"dem_cop_90\",\n" +
                "      \"description\": \"Copernicus DEM 90 m\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/dem_cop_90\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"dem_srtm\",\n" +
                "      \"description\": \"1 second elevation model\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/dem_srtm\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"dem_srtm_deriv\",\n" +
                "      \"description\": \"1 second elevation model derivatives\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/dem_srtm_deriv\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"esa_worldcover\",\n" +
                "      \"description\": \"ESA World Cover, global 10 m land use/land cover data from 2020.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/esa_worldcover\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"fc_ls\",\n" +
                "      \"description\": \"Landsat Fractional Cover Observations from Space\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/fc_ls\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"fc_ls_summary_annual\",\n" +
                "      \"description\": \"DE Africa Landsat Fractional Cover Percentiles\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/fc_ls_summary_annual\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gm_ls5_ls7_annual\",\n" +
                "      \"description\": \"Surface Reflectance Annual Geometric Median and Median Absolute Deviations, Landsat 5 and Landsat 7\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gm_ls5_ls7_annual\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gm_ls5_ls7_annual_lowres\",\n" +
                "      \"description\": \"Surface Reflectance Annual Geometric Median and Median Absolute Deviations, Landsat 5 and Landsat 7. Low resolution version used for visualisations.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gm_ls5_ls7_annual_lowres\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gm_ls8_annual\",\n" +
                "      \"description\": \"Surface Reflectance Annual Geometric Median and Median Absolute Deviations, Landsat 8\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gm_ls8_annual\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gm_ls8_annual_lowres\",\n" +
                "      \"description\": \"Surface Reflectance Annual Geometric Median and Median Absolute Deviations, Landsat 8. Low resolution version used for visualisations.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gm_ls8_annual_lowres\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gm_ls8_ls9_annual\",\n" +
                "      \"description\": \"Surface Reflectance Annual Geometric Median and Median Absolute Deviations, Landsat 8 and Landsat 9\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gm_ls8_ls9_annual\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gm_ls8_ls9_annual_lowres\",\n" +
                "      \"description\": \"Surface Reflectance Annual Geometric Median and Median Absolute Deviations, Landsat 8 and Landsat 9. Low resolution version used for visualisations.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gm_ls8_ls9_annual_lowres\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gm_s2_annual\",\n" +
                "      \"description\": \"Surface Reflectance Annual Geometric Median and Median Absolute Deviations, Sentinel-2\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gm_s2_annual\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gm_s2_annual_lowres\",\n" +
                "      \"description\": \"Annual Geometric Median, Sentinel-2 - Low Resolution mosaic\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gm_s2_annual_lowres\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gm_s2_semiannual\",\n" +
                "      \"description\": \"Surface Reflectance Semiannual Geometric Median and Median Absolute Deviations, Sentinel-2\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gm_s2_semiannual\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gm_s2_semiannual_lowres\",\n" +
                "      \"description\": \"Surface Reflectance Semiannual Geometric Median and Median Absolute Deviations, Sentinel-2. Low resolution version used for visualisations.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gm_s2_semiannual_lowres\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"gmw\",\n" +
                "      \"description\": \"Global Mangrove Watch data sourced from the UN Environment Program at https://data.unep-wcmc.org/datasets/45\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/gmw\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"io_lulc\",\n" +
                "      \"description\": \"Impact Observatory (ESRI) Landcover Classification\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/io_lulc\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"jers_sar_mosaic\",\n" +
                "      \"description\": \"JERS-1 SAR annual mosaic tiles generated for use in the Data Cube 25m pixel spacing, WGS84. These tiles are derived from the orignal JAXA mosaics with conversion to GeoTIFF.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/jers_sar_mosaic\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"ls5_sr\",\n" +
                "      \"description\": \"USGS Landsat 5 Collection 2 Level-2 Surface Reflectance\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/ls5_sr\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"ls5_st\",\n" +
                "      \"description\": \"USGS Landsat 5 Collection 2 Level-2 Surface Temperature\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/ls5_st\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"ls7_sr\",\n" +
                "      \"description\": \"USGS Landsat 7 Collection 2 Level-2 Surface Reflectance\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/ls7_sr\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"ls7_st\",\n" +
                "      \"description\": \"USGS Landsat 7 Collection 2 Level-2 Surface Temperature\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/ls7_st\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"ls8_sr\",\n" +
                "      \"description\": \"USGS Landsat 8 Collection 2 Level-2 Surface Reflectance\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/ls8_sr\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"ls8_st\",\n" +
                "      \"description\": \"USGS Landsat 8 Collection 2 Level-2 Surface Temperature\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/ls8_st\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"ls9_sr\",\n" +
                "      \"description\": \"USGS Landsat 9 Collection 2 Level-2 Surface Reflectance\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/ls9_sr\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"ls9_st\",\n" +
                "      \"description\": \"USGS Landsat 9 Collection 2 Level-2 Surface Temperature\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/ls9_st\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"nasadem\",\n" +
                "      \"description\": \"NASADEM from Microsoft's Planetary Computer\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/nasadem\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"ndvi_anomaly\",\n" +
                "      \"description\": \"Monthly NDVI Anomalies produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/ndvi_anomaly\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"ndvi_climatology_ls\",\n" +
                "      \"description\": \"Monthly NDVI Climatologies produced by Digital Earth Africa.\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/ndvi_climatology_ls\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"pc_s2_annual\",\n" +
                "      \"description\": \"Surface Reflectance Annual Clear Pixel Count, Sentinel-2\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/pc_s2_annual\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"rainfall_chirps_daily\",\n" +
                "      \"description\": \"Rainfall Estimates from Rain Gauge and Satellite Observations\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/rainfall_chirps_daily\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"rainfall_chirps_monthly\",\n" +
                "      \"description\": \"Rainfall Estimates from Rain Gauge and Satellite Observations\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/rainfall_chirps_monthly\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"s1_rtc\",\n" +
                "      \"description\": \"Sentinel 1 Gamma0 normalised radar backscatter\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/s1_rtc\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"s2_l2a\",\n" +
                "      \"description\": \"Sentinel-2a and Sentinel-2b imagery, processed to Level 2A (Surface Reflectance) and converted to Cloud Optimized GeoTIFFs\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/s2_l2a\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"wofs_ls\",\n" +
                "      \"description\": \"Historic Flood Mapping Water Observations from Space\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/wofs_ls\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"wofs_ls_summary_alltime\",\n" +
                "      \"description\": \"Water Observations from Space Alltime Statistics\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/wofs_ls_summary_alltime\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"title\": \"wofs_ls_summary_annual\",\n" +
                "      \"description\": \"Water Observations from Space Annual Statistics\",\n" +
                "      \"rel\": \"child\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/wofs_ls_summary_annual\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"rel\": \"root\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"conformsTo\": [\n" +
                "    \"https://api.stacspec.org/v1.0.0-beta.1/core\",\n" +
                "    \"https://api.stacspec.org/v1.0.0-beta.1/item-search\"\n" +
                "  ]\n" +
                "}";
    }

    private static String testCollection() {
        return "{\n" +
                "  \"stac_version\": \"1.0.0\",\n" +
                "  \"id\": \"alos_palsar_mosaic\",\n" +
                "  \"title\": \"alos_palsar_mosaic\",\n" +
                "  \"type\": \"Collection\",\n" +
                "  \"license\": null,\n" +
                "  \"description\": \"ALOS/PALSAR and ALOS-2/PALSAR-2 annual mosaic tiles generated for use in the Data Cube - 25m pixel spacing, WGS84. These tiles are derived from the orignal JAXA mosaics with conversion to GeoTIFF.\",\n" +
                "  \"properties\": {},\n" +
                "  \"providers\": [],\n" +
                "  \"extent\": {\n" +
                "    \"temporal\": {\n" +
                "      \"interval\": [\n" +
                "        [\n" +
                "          \"2007-07-02T11:59:59.500000+00:00\",\n" +
                "          \"2020-07-01T23:59:59.500000+00:00\"\n" +
                "        ]\n" +
                "      ]\n" +
                "    },\n" +
                "    \"spatial\": {\n" +
                "      \"bbox\": [\n" +
                "        [\n" +
                "          -19.999995750555073,\n" +
                "          -35.01972686708247,\n" +
                "          55.0000149402844,\n" +
                "          40.00000334798672\n" +
                "        ]\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"links\": [\n" +
                "    {\n" +
                "      \"rel\": \"items\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac/collections/alos_palsar_mosaic/items\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"rel\": \"root\",\n" +
                "      \"href\": \"https://explorer.digitalearth.africa/stac\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }

    private static String testCollections() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Test.class.getResourceAsStream("collections.json")))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private static String testItems() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Test.class.getResourceAsStream("items_1.json")))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
