package ro.cs.tao.datasource.remote.creodias.landsat8;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.NoDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

import java.util.*;

public class CreoDIASLandsat8ParameterProvider implements ParameterProvider {

    private static final String[] sensors;
    private static final Map<String, Map<String, DataSourceParameter>> parameters;
    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Landsat8" };
        parameters = Collections.unmodifiableMap(
                new HashMap<String, Map<String, DataSourceParameter>>() {{
                    put("Landsat8", new LinkedHashMap<String, DataSourceParameter>() {{
                        put("processingLevel",  new DataSourceParameter("processingLevel", String.class, "LEVEL1T"));
                        put("productType",  new DataSourceParameter("productType", String.class, "L1T"));
                        put("path",  new DataSourceParameter("path", Short.class));
                        put("row",  new DataSourceParameter("row", Short.class));
                        put("startDate",  new DataSourceParameter("startDate", Date.class, true));
                        put("completionDate",  new DataSourceParameter("completionDate", Date.class));
                        put("geometry",  new DataSourceParameter("geometry", Polygon2D.class, true));
                        put("cloudCover", new DataSourceParameter("cloudCover", Short.class, 100));
                    }});
                }});
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Landsat8", new NoDownloadStrategy(targetFolder, new Properties()));
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
