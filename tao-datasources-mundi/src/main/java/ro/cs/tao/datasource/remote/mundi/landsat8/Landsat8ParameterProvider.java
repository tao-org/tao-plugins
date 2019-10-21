package ro.cs.tao.datasource.remote.mundi.landsat8;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;

import java.util.*;

public class Landsat8ParameterProvider extends AbstractParameterProvider {

    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    //put("Landsat8", new NoDownloadStrategy(targetFolder, new Properties()));
                    put("Landsat8", new Landsat8Strategy(targetFolder));
                }});
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }

}
