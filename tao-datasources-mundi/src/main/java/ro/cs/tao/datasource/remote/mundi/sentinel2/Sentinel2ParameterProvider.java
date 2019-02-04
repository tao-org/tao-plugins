package ro.cs.tao.datasource.remote.mundi.sentinel2;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.NoDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

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
                        put(ParameterName.create("processingLevel", "processingLevel", "Processing Level"),
                            new DataSourceParameter("processingLevel", String.class, "L1C", false,
                                                    "L1A", "L1B", "L1C", "L2A"));
                        put(ParameterName.create(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type"),
                            new DataSourceParameter("productType", String.class, "IMAGE", false,
                                                    "IMAGE", "S2MSI2Ap"));
                        put(ParameterName.create(CommonParameterNames.RELATIVE_ORBIT, "orbitNumber", "Relative Orbit"),
                            new DataSourceParameter("orbitNumber", Short.class));
                        put(ParameterName.create(CommonParameterNames.START_DATE, "timeStart", "Start Date"),
                            new DataSourceParameter("timeStart", Date.class, true));
                        put(ParameterName.create(CommonParameterNames.END_DATE, "timeEnd", "End Date"),
                            new DataSourceParameter("timeEnd", Date.class));
                        put(ParameterName.create(CommonParameterNames.FOOTPRINT, "geometry", "Area of Interest"),
                            new DataSourceParameter("geometry", Polygon2D.class, true));
                        put(ParameterName.create(CommonParameterNames.CLOUD_COVER, "cloudCover", "Cloud Cover"),
                            new DataSourceParameter("cloudCover", Short.class));
                        put(ParameterName.create(CommonParameterNames.PRODUCT, "title", "Product Name"),
                            new DataSourceParameter("title", String.class));
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
