package ro.cs.tao.datasource.remote.earthdata.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.earthdata.EarthDataSource;
import ro.cs.tao.datasource.remote.earthdata.download.EarthDataDownloadStrategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

public class EarthDataParameterProvider extends AbstractParameterProvider {

    public EarthDataParameterProvider(EarthDataSource dataSource) {
        super();
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        final Properties properties = new Properties();
        properties.put("auto.uncompress", "false");
        final EarthDataDownloadStrategy strategy = new EarthDataDownloadStrategy(dataSource, targetFolder, properties);
        HashMap<String, ProductFetchStrategy> fetchers = new HashMap<>();
        final String[] sensors = getSupportedSensors();
        for (String sensor : sensors) {
            fetchers.put(sensor, strategy);
        }
        productFetchers = Collections.unmodifiableMap(fetchers);
    }
}
