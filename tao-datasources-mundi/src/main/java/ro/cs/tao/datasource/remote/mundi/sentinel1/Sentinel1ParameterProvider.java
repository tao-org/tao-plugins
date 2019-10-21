package ro.cs.tao.datasource.remote.mundi.sentinel1;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.mundi.DownloadStrategy;

import java.util.*;

public class Sentinel1ParameterProvider extends AbstractParameterProvider {

    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    //put("Sentinel1", new NoDownloadStrategy(targetFolder, new Properties()));
                    put("Sentinel1", new DownloadStrategy(targetFolder, new Properties()));
                }});
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }

}
