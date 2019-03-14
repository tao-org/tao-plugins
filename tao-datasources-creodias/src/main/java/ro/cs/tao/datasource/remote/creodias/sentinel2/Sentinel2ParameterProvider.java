package ro.cs.tao.datasource.remote.creodias.sentinel2;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.NoDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.Tuple;

import java.util.*;

public class Sentinel2ParameterProvider implements ParameterProvider {

    private static final String[] sensors;
    private static final Map<String, Map<ParameterName, DataSourceParameter>> parameters;
    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Sentinel2" };
        parameters = Collections.unmodifiableMap(
                new HashMap<String, Map<ParameterName, DataSourceParameter>>() {{
                    put("Sentinel2", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                        Tuple<ParameterName, DataSourceParameter> parameter =
                                ParameterProvider.createParameter("processingLevel", "processingLevel", "Processing Level", String.class, "LEVEL1C", false, "LEVEL1C", "LEVEL2A", "LEVEL2AP", "LEVELL1C", "LEVELL2A");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type", String.class, "L1C", false, "L1C", "L2A", "N0204");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.RELATIVE_ORBIT, "orbitNumber", "Relative Orbit", Short.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "startDate", "Start Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "completionDate", "End Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "geometry", "Area of Interest", Polygon2D.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.CLOUD_COVER, "cloudCover", "Cloud Cover", Short.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT, "productIdentifier", "Product Name", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                    }});
                }});
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel2", new NoDownloadStrategy(targetFolder, new Properties()));
                }});
    }

    @Override
    public Map<String, Map<ParameterName, DataSourceParameter>> getSupportedParameters() {
        return parameters;
    }

    @Override
    public String[] getSupportedSensors() {
        return sensors;
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }
}
