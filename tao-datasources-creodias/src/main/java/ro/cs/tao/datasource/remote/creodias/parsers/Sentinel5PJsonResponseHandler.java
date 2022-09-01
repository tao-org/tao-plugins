package ro.cs.tao.datasource.remote.creodias.parsers;

import ro.cs.tao.datasource.remote.creodias.model.s1.Feature;
import ro.cs.tao.datasource.remote.creodias.model.s1.Result;
import ro.cs.tao.datasource.remote.creodias.model.s1.ResultSet;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.serialization.JsonMapper;
import ro.cs.tao.utils.ExceptionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class Sentinel5PJsonResponseHandler implements JSonResponseHandler<EOProduct> {
    private final Logger logger = Logger.getLogger(Sentinel5PJsonResponseHandler.class.getName());

    @Override
    public List<EOProduct> readValues(String content, AttributeFilter... filters) throws IOException {
        List<EOProduct> products = new ArrayList<>();
        try {
            final ResultSet results = JsonMapper.instance().readValue(content, ResultSet.class);
            final List<Feature> features = results.getFeatures();
            if (features != null) {
                for (Feature feature : features) {
                    try {
                        Result result = feature.getProperties();
                        EOProduct product = new EOProduct();
                        product.setName(result.getTitle());
                        product.setId(feature.getId());
                        product.setFormatType(DataFormat.RASTER);
                        product.setSensorType(SensorType.ATMOSPHERIC);
                        product.setPixelType(PixelType.FLOAT32);
                        product.setWidth(-1);
                        product.setHeight(-1);
                        final LinkedHashMap geometry = (LinkedHashMap) feature.getGeometry();
                        Polygon2D footprint = null;
                        if (geometry != null) {
                            final String type = (String) geometry.get("type");
                            switch (type.toLowerCase()) {
                                case "polygon":
                                    List<List<List<Object>>> cPoly = (List<List<List<Object>>>) geometry.get("coordinates");
                                    footprint = new Polygon2D();
                                    for (List<Object> doubles : cPoly.get(0)) {
                                        // we don't directly cast to Double because Jackson deserializes "0" as an Integer
                                        final Object x = doubles.get(0);
                                        final Object y = doubles.get(1);
                                        footprint.append(x instanceof Double ? (Double) x : Double.valueOf(x.toString()),
                                                         y instanceof Double ? (Double) y : Double.valueOf(y.toString()));
                                    }
                                    break;
                                case "multipolygon":
                                    List<List<List<List<Object>>>> cMultiPoly = (List<List<List<List<Object>>>>) geometry.get("coordinates");
                                    footprint = new Polygon2D();
                                    for (List<Object> doubles : cMultiPoly.get(0).get(0)) {
                                        // we don't directly cast to Double because Jackson deserializes "0" as an Integer
                                        final Object x = doubles.get(0);
                                        final Object y = doubles.get(1);
                                        footprint.append(x instanceof Double ? (Double) x : Double.valueOf(x.toString()),
                                                         y instanceof Double ? (Double) y : Double.valueOf(y.toString()));
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
                        product.setProductType("Sentinel5P");
                        product.setSatelliteName("Sentinel5P");
                        product.setLocation(result.getProductIdentifier());
                        product.setAcquisitionDate(new DateAdapter().unmarshal(result.getStartDate()));
                        product.setQuicklookLocation(result.getThumbnail());
                        product.addAttribute("productType", result.getProductType());
                        Map<String, Object> additionalProperties = feature.getAdditionalProperties();
                        for (Map.Entry<String, Object> entry : additionalProperties.entrySet()) {
                            product.addAttribute(entry.getKey(), String.valueOf(entry.getValue()));
                        }
                        products.add(product);
                    } catch (Exception ex) {
                        logger.warning("Error parsing JSON: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warning(ExceptionUtils.getStackTrace(logger, e));
        }
        return products;
    }

    @Override
    public long countValues(String content) throws IOException {
        ResultSet results = JsonMapper.instance().readValue(content, ResultSet.class);
        return results != null ? results.getProperties().getTotalResults().longValue() : 0;
    }
}
