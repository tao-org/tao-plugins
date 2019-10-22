package ro.cs.tao.datasource.remote.creodias.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.NoDownloadStrategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by vnetoiu on 10/22/2019.
 */
public class CreodiasParameterProvider extends AbstractParameterProvider {

    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1", new NoDownloadStrategy(targetFolder, new Properties()));
                    put("Sentinel2", new NoDownloadStrategy(targetFolder, new Properties()));
                    put("Landsat8", new NoDownloadStrategy(targetFolder, new Properties()));
                }});
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }
}
