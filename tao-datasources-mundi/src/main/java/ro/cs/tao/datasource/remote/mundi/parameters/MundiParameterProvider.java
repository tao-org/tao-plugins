package ro.cs.tao.datasource.remote.mundi.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.mundi.DownloadStrategy;
import ro.cs.tao.datasource.remote.mundi.landsat8.Landsat8Strategy;
import ro.cs.tao.datasource.remote.mundi.sentinel2.Sentinel2DownloadStrategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by vnetoiu on 10/22/2019.
 */
public class MundiParameterProvider extends AbstractParameterProvider {

    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1", new DownloadStrategy(targetFolder, new Properties()));
                    put("Sentinel2", new Sentinel2DownloadStrategy(targetFolder));
                    put("Landsat8", new Landsat8Strategy(targetFolder));
                }});
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }
}
