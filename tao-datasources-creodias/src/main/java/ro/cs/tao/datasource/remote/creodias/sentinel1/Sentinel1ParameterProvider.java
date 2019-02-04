package ro.cs.tao.datasource.remote.creodias.sentinel1;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.NoDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

import java.util.*;

public class Sentinel1ParameterProvider implements ParameterProvider {

    private static final String[] sensors;
    private static final Map<String, Map<ParameterName, DataSourceParameter>> parameters;
    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Sentinel1" };
        parameters = Collections.unmodifiableMap(
                new HashMap<String, Map<ParameterName, DataSourceParameter>>() {{
                    put("Sentinel1", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                        put(ParameterName.create("processingLevel", "processingLevel", "Processing Level"),
                            new DataSourceParameter("processingLevel", String.class, "LEVEL1", false,
                                                    "LEVEL0", "LEVEL1", "LEVEL2"));
                        put(ParameterName.create(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type"),
                            new DataSourceParameter("productType", String.class, "SLC", false,
                                                    "GRD", "OCN", "RAW", "SLC"));
                        put(ParameterName.create("sensorMode", "sensorMode", "Sensor Mode"),
                            new DataSourceParameter("sensorMode", String.class, "IW", false,
                                                    "EW", "IW", "SM", "WV"));
                        put(ParameterName.create("orbitDirection", "orbitDirection", "Orbit Direction"),
                            new DataSourceParameter("orbitDirection", String.class));
                        put(ParameterName.create(CommonParameterNames.RELATIVE_ORBIT, "orbitNumber", "Relative Orbit"),
                            new DataSourceParameter("orbit", Short.class));
                        put(ParameterName.create(CommonParameterNames.START_DATE, "startDate", "Start Date"),
                            new DataSourceParameter("startDate", Date.class, true));
                        put(ParameterName.create(CommonParameterNames.END_DATE, "completionDate", "End Date"),
                            new DataSourceParameter("completionDate", Date.class));
                        put(ParameterName.create(CommonParameterNames.FOOTPRINT, "geometry", "Area of Interest"),
                            new DataSourceParameter("geometry", Polygon2D.class, true));
                        put(ParameterName.create(CommonParameterNames.POLARISATION, "polarisation", "Polarisation"),
                            new DataSourceParameter("polarisation", String.class));
                    }});
                }});
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1", new NoDownloadStrategy(targetFolder, new Properties()));
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
