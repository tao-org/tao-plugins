package ro.cs.tao.datasource.remote.creodias.parsers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.datasource.remote.creodias.model.l8.Feature;
import ro.cs.tao.datasource.remote.creodias.model.l8.Result;
import ro.cs.tao.datasource.remote.creodias.model.l8.ResultSet;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

public class Landsat8JsonResponseHandler implements JSonResponseHandler<EOProduct> {
    private Logger logger = Logger.getLogger(Landsat8JsonResponseHandler.class.getName());

    @Override
    public List<EOProduct> readValues(String content, AttributeFilter... filters) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
        List<EOProduct> products = new ArrayList<>();
        try {
            ResultSet results = mapper.readValue(content, ResultSet.class);
            List<Feature> features = results.getFeatures();
            if (features != null) {
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
                        final LinkedHashMap geometry = (LinkedHashMap) feature.getGeometry();
                        Polygon2D footprint = null;
                        if (geometry != null) {
                            final String type = (String) geometry.get("type");
                            switch (type.toLowerCase()) {
                                case "polygon":
                                    List<List<List<Double>>> cPoly = (List<List<List<Double>>>) geometry.get("coordinates");
                                    footprint = new Polygon2D();
                                    for (List<Double> doubles : cPoly.get(0)) {
                                        footprint.append(doubles.get(0), doubles.get(1));
                                    }
                                    break;
                                case "multipolygon":
                                    List<List<List<List<Double>>>> cMultiPoly = (List<List<List<List<Double>>>>) geometry.get("coordinates");
                                    footprint = new Polygon2D();
                                    for (List<Double> doubles : cMultiPoly.get(0).get(0)) {
                                        footprint.append(doubles.get(0), doubles.get(1));
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                        if (footprint != null) {
                            product.setGeometry(footprint.toWKT());
                        }
                        //product.setProductType(result.getProductType());
                        product.setProductType("Landsat8");
                        product.setLocation(result.getProductIdentifier());
                        product.setAcquisitionDate(new DateAdapter().unmarshal(result.getStartDate()));
                        product.addAttribute("cloudcoverpercentage", String.valueOf(result.getCloudCover()));
                        product.addAttribute("tiles", String.format("%03d", result.getPath()) +
                                String.format("%03d", result.getRow()));
                        products.add(product);
                    } catch (Exception ex) {
                        logger.warning("Error parsing JSON: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error parsing JSON: " + e.getMessage());
        }
        return products;
    }

    @Override
    public long countValues(String content) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);
        ResultSet results = mapper.readValue(content, ResultSet.class);
        return results != null ? results.getProperties().getTotalResults().longValue() : 0;
    }
}
