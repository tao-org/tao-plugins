package ro.cs.tao.datasource.remote.creodias.parsers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.datasource.remote.creodias.model.common.Geometry;
import ro.cs.tao.datasource.remote.creodias.model.common.Geometry2;
import ro.cs.tao.datasource.remote.creodias.model.s2.*;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.products.sentinels.SentinelProductHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Sentinel2JsonResponseHandler implements JSonResponseHandler<EOProduct> {
    private Logger logger = Logger.getLogger(Sentinel1JsonResponseHandler.class.getName());

    @Override
    public List<EOProduct> readValues(String content, AttributeFilter... filters) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
        List<EOProduct> products = new ArrayList<>();
        try {
            ResultSet results = mapper.readValue(content, ResultSet.class);
            List<Feature> features = results.getFeatures();
            if (features != null) {
                ProductHelper productHelper;
                for (Feature feature : features) {
                    try {
                        Result result = feature.getProperties();
                        EOProduct product = new EOProduct();
                        product.setName(result.getTitle());
                        product.setId(feature.getId());
                        product.setFormatType(DataFormat.RASTER);
                        product.setSensorType(SensorType.OPTICAL);
                        product.setPixelType(PixelType.UINT16);
                        product.setWidth(-1);
                        product.setHeight(-1);
                        Geometry geometry = feature.getGeometry();
                        if (geometry != null) {
                            List<List<Double>> coordinates = geometry.getCoordinates().get(0);
                            Polygon2D footprint = new Polygon2D();
                            for (List<Double> doubles : coordinates) {
                                footprint.append(doubles.get(0), doubles.get(1));
                            }
                            product.setGeometry(footprint.toWKT());
                        }
                        switch (result.getProductType()) {
                            case "L1C":
                                product.addAttribute("productType", "S2MSI1C");
                                break;
                            case "L2A":
                                product.addAttribute("productType", "S2MSI2A");
                                break;
                        }
                        product.setProductType("Sentinel2");
                        product.setLocation(result.getProductIdentifier());
                        product.setAcquisitionDate(new DateAdapter().unmarshal(result.getStartDate()));
                        product.addAttribute("cloudcoverpercentage", String.valueOf(result.getCloudCover()));
                        productHelper = SentinelProductHelper.create(result.getTitle());
                        product.addAttribute("tiles", ((Sentinel2ProductHelper) productHelper).getTileIdentifier());
                        products.add(product);
                    } catch (Exception ex) {
                        logger.warning("Error parsing JSON: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            ResultSet2 results = mapper.readValue(content, ResultSet2.class);
            mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
            List<Feature2> features = results.getFeatures();
            if (features != null) {
                for (Feature2 feature : features) {
                    try {
                        Result result = feature.getProperties();
                        EOProduct product = new EOProduct();
                        product.setName(result.getTitle());
                        product.setId(feature.getId());
                        product.setFormatType(DataFormat.RASTER);
                        product.setSensorType(SensorType.OPTICAL);
                        product.setPixelType(PixelType.UINT16);
                        product.setWidth(-1);
                        product.setHeight(-1);
                        Geometry2 geometry = feature.getGeometry();
                        if (geometry != null) {
                            List<List<Double>> coordinates = geometry.getCoordinates().get(0).get(0);
                            Polygon2D footprint = new Polygon2D();
                            for (List<Double> doubles : coordinates) {
                                footprint.append(doubles.get(0), doubles.get(1));
                            }
                            product.setGeometry(footprint.toWKT());
                        }
                        switch (result.getProductType()) {
                            case "L1C":
                                product.addAttribute("productType", "S2MSI1C");
                                break;
                            case "L2A":
                                product.addAttribute("productType", "S2MSI2A");
                                break;
                        }
                        product.setProductType("Sentinel2");
                        product.setLocation(result.getProductIdentifier());
                        product.setAcquisitionDate(new DateAdapter().unmarshal(result.getStartDate()));
                        product.addAttribute("cloudcoverpercentage", String.valueOf(result.getCloudCover()));
                        products.add(product);
                    } catch (Exception ex) {
                        logger.warning("Error parsing JSON: " + ex.getMessage());
                    }
                }
            }
        }
        return products;
    }

    @Override
    public long countValues(String content) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
        try {
            ResultSet results = mapper.readValue(content, ResultSet.class);
            return results != null ? results.getProperties().getTotalResults().longValue() : 0;
        } catch (Exception e) {
            ResultSet2 results = mapper.readValue(content, ResultSet2.class);
            return results != null ? results.getProperties().getTotalResults().longValue() : 0;
        }
    }
}
