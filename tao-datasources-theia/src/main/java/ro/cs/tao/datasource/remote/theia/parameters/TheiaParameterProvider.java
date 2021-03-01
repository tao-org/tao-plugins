package ro.cs.tao.datasource.remote.theia.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.theia.TheiaDataSource;
import ro.cs.tao.datasource.remote.theia.download.TheiaDownloadStrategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

public class TheiaParameterProvider extends AbstractParameterProvider {

    public TheiaParameterProvider(TheiaDataSource dataSource) {
        super();
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        final Properties properties = new Properties();
        properties.put("auto.uncompress", "false");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Landsat8", new TheiaDownloadStrategy(dataSource, targetFolder, properties));
                    put("Pleiades", new TheiaDownloadStrategy(dataSource, targetFolder, properties));
                    put("Sentinel2", new TheiaDownloadStrategy(dataSource, targetFolder, properties));
                    put("Venus", new TheiaDownloadStrategy(dataSource, targetFolder, properties));
                }});
    }
}
