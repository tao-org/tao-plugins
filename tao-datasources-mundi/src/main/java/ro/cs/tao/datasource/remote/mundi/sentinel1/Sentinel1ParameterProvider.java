package ro.cs.tao.datasource.remote.mundi.sentinel1;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.mundi.DownloadStrategy;
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
                            new DataSourceParameter("processingLevel", String.class, "L1_", false,
                                                    "L0_", "L1_", "L2_"));
                        put(ParameterName.create(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type"),
                            new DataSourceParameter("productType", String.class, "SLC", false,
                                                    "GRD", "OCN", "RAW", "SLC"));
                        put(ParameterName.create("sensorMode", "sensorMode", "Sensor Mode"),
                            new DataSourceParameter("sensorMode", String.class, "IW_", false,
                                                    "EW_", "IW_", "SM_", "WV_"));
                        put(ParameterName.create("orbitDirection", "orbitDirection", "Orbit Direction"),
                            new DataSourceParameter("orbitDirection", String.class, null, false,
                                                    "ASCENDING", "DESCENDING"));
                        put(ParameterName.create(CommonParameterNames.TILE, "orbitNumber", "Relative Orbit"),
                            new DataSourceParameter("orbitNumber", Short.class));
                        put(ParameterName.create(CommonParameterNames.START_DATE, "timeStart", "Start Date"),
                            new DataSourceParameter("timeStart", Date.class, true));
                        put(ParameterName.create(CommonParameterNames.END_DATE, "timeEnd", "End Date"),
                            new DataSourceParameter("timeEnd", Date.class));
                        put(ParameterName.create(CommonParameterNames.FOOTPRINT, "geometry", "Area of Interest"),
                            new DataSourceParameter("geometry", Polygon2D.class, true));
                        put(ParameterName.create(CommonParameterNames.POLARISATION, "polarisationChannels", "Polarisation"),
                            new DataSourceParameter("polarisationChannels", String.class, null, false,
                                                    "HH/VV", "HH/HV", "VV/VH", "HH", "VV", "HV", "VH"));
                    }});
                }});
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    //put("Sentinel1", new NoDownloadStrategy(targetFolder, new Properties()));
                    put("Sentinel1", new DownloadStrategy(targetFolder, new Properties()));
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
