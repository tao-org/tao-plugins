package ro.cs.tao.datasource.remote.creodias.parsers;

import ro.cs.tao.datasource.remote.creodias.model.s2.Feature;
import ro.cs.tao.datasource.remote.creodias.model.s2.Result;
import ro.cs.tao.datasource.remote.creodias.model.s2.ResultSet;
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
import ro.cs.tao.serialization.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

public class Sentinel2JsonResponseHandler implements JSonResponseHandler<EOProduct> {
    private final Logger logger = Logger.getLogger(Sentinel2JsonResponseHandler.class.getName());

    @Override
    public List<EOProduct> readValues(String content, AttributeFilter... filters) throws IOException {
        List<EOProduct> products = new ArrayList<>();
        try {
            ResultSet results = JsonMapper.instance().readValue(content, ResultSet.class);
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
                        product.setProductType("Sentinel2");
                        product.setSatelliteName("Sentinel2");
                        product.setLocation(result.getProductIdentifier());
                        product.setAcquisitionDate(new DateAdapter().unmarshal(result.getStartDate()));
                        product.setQuicklookLocation(result.getThumbnail());
                        product.addAttribute("cloudcoverpercentage", String.valueOf(result.getCloudCover()));
                        productHelper = SentinelProductHelper.create(result.getTitle());
                        product.addAttribute("tiles", ((Sentinel2ProductHelper) productHelper).getTileIdentifier());
                        product.addAttribute("productType", result.getProductType());
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
        ResultSet results = JsonMapper.instance().readValue(content, ResultSet.class);
        return results != null ? results.getProperties().getTotalResults().longValue() : 0;
    }
}
