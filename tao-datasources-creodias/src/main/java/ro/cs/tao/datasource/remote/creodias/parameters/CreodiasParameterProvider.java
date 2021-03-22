package ro.cs.tao.datasource.remote.creodias.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.datasource.remote.creodias.download.CreoDIASDownloadStrategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

/**
 * Created by vnetoiu on 10/22/2019.
 */
public class CreodiasParameterProvider extends AbstractParameterProvider {

    public CreodiasParameterProvider(CreoDiasDataSource dataSource) {
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    //put("Sentinel1", new NoDownloadStrategy(dataSource, targetFolder, new Properties()));
                    put("Sentinel1", new CreoDIASDownloadStrategy(dataSource, targetFolder, new Properties()));
                    //put("Sentinel2", new NoDownloadStrategy(dataSource, targetFolder, new Properties()));
                    put("Sentinel2", new CreoDIASDownloadStrategy(dataSource, targetFolder, new Properties()));
                    //put("Landsat8", new NoDownloadStrategy(dataSource, targetFolder, new Properties()));
                    put("Landsat8", new CreoDIASDownloadStrategy(dataSource, targetFolder, new Properties()));
                }});
    }
}
