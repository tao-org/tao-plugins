package ro.cs.tao.datasource.remote.creodias.parsers;

import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.datasource.remote.creodias.model.common.Geometry;
import ro.cs.tao.datasource.remote.creodias.model.s1.Feature;
import ro.cs.tao.datasource.remote.creodias.model.s1.Result;
import ro.cs.tao.datasource.remote.creodias.model.s1.ResultSet;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.OrbitDirection;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Sentinel1JsonResponseHandler implements JSonResponseHandler<EOProduct> {
    private Logger logger = Logger.getLogger(Sentinel1JsonResponseHandler.class.getName());

    @Override
    public List<EOProduct> readValues(String content, AttributeFilter... filters) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ResultSet results = mapper.readValue(content, ResultSet.class);
        List<EOProduct> products = new ArrayList<>();
        List<Feature> features = results.getFeatures();
        if (features != null) {
            for (Feature feature : features) {
                try {
                    Result result = feature.getProperties();
                    EOProduct product = new EOProduct();
                    product.setName(result.getTitle());
                    product.setId(feature.getId());
                    product.setFormatType(DataFormat.RASTER);
                    product.setSensorType(SensorType.RADAR);
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
                    product.setProductType(result.getProductType());
                    product.setLocation(result.getProductIdentifier());
                    product.setAcquisitionDate(new DateAdapter().unmarshal(result.getStartDate()));
                    product.addAttribute("orbitdirection",
                                         ("ascending".equals(result.getOrbitDirection()) ?
                                                 OrbitDirection.ASCENDING : OrbitDirection.DESCENDING).name());
                    products.add(product);
                } catch (Exception ex) {
                    logger.warning("Error parsing JSON: " + ex.getMessage());
                }
            }
        }
        return products;
    }

    @Override
    public long countValues(String content) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ResultSet results = mapper.readValue(content, ResultSet.class);
        return results != null ? results.getProperties().getTotalResults().longValue() : 0;
    }
}
