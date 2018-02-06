package ro.cs.tao.datasource.remote.usgs.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.ParameterDescriptor;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.usgs.download.LandsatDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Cosmin Cara
 */
public class LandsatParameterProvider implements ParameterProvider {

    private static String[] sensors;
    private static Map<String, Map<String, ParameterDescriptor>> parameters;
    private static Map<String, ProductFetchStrategy> productFetchers;

    public LandsatParameterProvider() {
        if (sensors == null) {
            sensors = new String[] { "Landsat8" };
        }
        if (parameters == null) {
            parameters = Collections.unmodifiableMap(
                    new HashMap<String, Map<String, ParameterDescriptor>>() {{
                        put("Landsat8", new HashMap<String, ParameterDescriptor>() {{
                            put("satellite_name", new ParameterDescriptor("satellite_name", String.class, "landsat-8"));
                            put("date_from", new ParameterDescriptor("date_from", Date.class));
                            put("date_to", new ParameterDescriptor("date_to", Date.class));
                            put("footprint", new ParameterDescriptor("footprint", Polygon2D.class));
                            put("cloud_from", new ParameterDescriptor("cloud_from", Double.class, 0.));
                            put("cloud_to", new ParameterDescriptor("cloud_to", Double.class, 100.));
                            put("row", new ParameterDescriptor("row", Integer.class));
                            put("path", new ParameterDescriptor("path", Integer.class));
                            put("row_path", new ParameterDescriptor("row_path", String[].class));
                        }});
                    }});
        }
        if (productFetchers == null) {
            final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
            productFetchers = Collections.unmodifiableMap(
                    new HashMap<String, ProductFetchStrategy>() {{
                        put("Landsat8", new LandsatDownloadStrategy(targetFolder));
                    }});
        }
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }

    @Override
    public Map<String, Map<String, ParameterDescriptor>> getSupportedParameters() {
        return parameters;
    }

    @Override
    public String[] getSupportedSensors() {
        return sensors;
    }
}
