package ro.cs.tao.datasource.remote.mundi.sentinel2;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.Tuple;

import java.util.*;

public class Sentinel2ParameterProvider implements ParameterProvider {

    private static final String[] sensors;
    private static final Map<String, Map<String, DataSourceParameter>> parameters;
    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Sentinel2" };
        parameters = Collections.unmodifiableMap(
                new LinkedHashMap<String, Map<String, DataSourceParameter>>() {{
                    put("Sentinel2", new LinkedHashMap<String, DataSourceParameter>() {{
                        Tuple<String, DataSourceParameter> parameter =
                                ParameterProvider.createParameter("processingLevel", "processingLevel", "Processing Level",
                                                                  String.class, "L1C", false, "L1A", "L1B", "L1C", "L2A");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type",
                                                                      String.class, "IMAGE", false, "IMAGE", "S2MSI2Ap");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.RELATIVE_ORBIT, "orbitNumber", "Relative Orbit", Short.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "timeStart", "Start Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "timeEnd", "End Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "geometry", "Area of Interest", Polygon2D.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.CLOUD_COVER, "cloudCover", "Cloud Cover", Short.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT, "title", "Product Name", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.TILE, "uid", "UTM Tile", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                    }});
                }});
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    //put("Sentinel2", new NoDownloadStrategy(targetFolder, new Properties()));
                    put("Sentinel2", new Sentinel2DownloadStrategy(targetFolder));
                }});
    }

    @Override
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        return parameters;
    }

    @Override
    public String[] getSupportedSensors() {
        return sensors;
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }
}
