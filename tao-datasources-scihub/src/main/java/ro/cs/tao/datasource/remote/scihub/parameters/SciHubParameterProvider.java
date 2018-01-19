package ro.cs.tao.datasource.remote.scihub.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.ParameterDescriptor;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel2DownloadStrategy;
import ro.cs.tao.datasource.remote.scihub.download.SentinelDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Cosmin Cara
 */
public final class SciHubParameterProvider implements ParameterProvider {

    private static String[] sensors;
    private static Map<String, Map<String, ParameterDescriptor>> parameters;
    private static Map<String, ProductFetchStrategy> productFetchers;

    public SciHubParameterProvider() {
        if (sensors == null) {
            sensors = new String[] { "Sentinel1", "Sentinel2" };
        }
        if (parameters == null) {
            parameters = Collections.unmodifiableMap(
                    new HashMap<String, Map<String, ParameterDescriptor>>() {{
                        put("Sentinel1", new HashMap<String, ParameterDescriptor>() {{
                            put("platformName", new ParameterDescriptor("platformName", String.class, "Sentinel-1"));
                            put("beginPosition", new ParameterDescriptor("beginPosition", Date.class));
                            put("endPosition", new ParameterDescriptor("endPosition", Date.class));
                            put("footprint", new ParameterDescriptor("footprint", Polygon2D.class));
                            put("productType", new ParameterDescriptor("productType", String.class, "SLC"));
                            put("polarisationMode", new ParameterDescriptor("polarisationMode", String.class));
                            put("sensorOperationalMode", new ParameterDescriptor("sensorOperationalMode", String.class));
                            put("relativeOrbitNumber", new ParameterDescriptor("relativeOrbitNumber", String.class));
                        }});
                        put("Sentinel2", new HashMap<String, ParameterDescriptor>() {{
                            put("platformName", new ParameterDescriptor("platformName", String.class, "Sentinel-2"));
                            put("beginPosition", new ParameterDescriptor("beginPosition", Date.class));
                            put("endPosition", new ParameterDescriptor("endPosition", Date.class));
                            put("footprint", new ParameterDescriptor("footprint", Polygon2D.class));
                            put("productType", new ParameterDescriptor("productType", String.class, "S2MSI1C"));
                            put("cloudcoverpercentage", new ParameterDescriptor("cloudcoverpercentage", Double.class, 100.));
                            put("relativeOrbitNumber", new ParameterDescriptor("relativeOrbitNumber", Short.class));
                        }});
                    }});
        }
        if (productFetchers == null) {
            final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
            productFetchers = Collections.unmodifiableMap(
                    new HashMap<String, ProductFetchStrategy>() {{
                        put("Sentinel1", new SentinelDownloadStrategy(targetFolder));
                        put("Sentinel2", new Sentinel2DownloadStrategy(targetFolder));
                    }});
        }
    }

    @Override
    public String[] getSupportedSensors() {
        return sensors;
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }

    @Override
    public Map<String, Map<String, ParameterDescriptor>> getSupportedParameters() {
        return parameters;
    }
}
