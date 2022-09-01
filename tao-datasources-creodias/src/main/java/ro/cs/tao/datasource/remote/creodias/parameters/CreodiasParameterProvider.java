package ro.cs.tao.datasource.remote.creodias.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.ProductPathBuilder;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.datasource.remote.creodias.download.CreoDIASDownloadStrategy;
import ro.cs.tao.datasource.remote.creodias.download.CreoDIASL8PathBuilder;
import ro.cs.tao.datasource.remote.creodias.download.CreoDIASS3PathBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

/**
 * Created by vnetoiu on 10/22/2019.
 */
public class CreodiasParameterProvider extends AbstractParameterProvider {

    public CreodiasParameterProvider(CreoDiasDataSource dataSource) {
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        Properties properties = new Properties();
        CreoDIASDownloadStrategy s1Strategy = new CreoDIASDownloadStrategy(dataSource, targetFolder, properties);
        CreoDIASDownloadStrategy s2Strategy = new CreoDIASDownloadStrategy(dataSource, targetFolder, properties);
        CreoDIASDownloadStrategy s5Strategy = new CreoDIASDownloadStrategy(dataSource, targetFolder, properties);
        properties = new Properties();
        properties.put(ProductPathBuilder.PATH_BUILDER_CLASS, CreoDIASS3PathBuilder.class.getName());
        CreoDIASDownloadStrategy s3Strategy = new CreoDIASDownloadStrategy(dataSource, targetFolder, properties);
        properties = new Properties();
        properties.put(ProductPathBuilder.PATH_BUILDER_CLASS, CreoDIASL8PathBuilder.class.getName());
        CreoDIASDownloadStrategy l8Strategy = new CreoDIASDownloadStrategy(dataSource, targetFolder, properties);
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1", s1Strategy);
                    put("Sentinel2", s2Strategy);
                    put("Sentinel3", s3Strategy);
                    put("Sentinel5P", s5Strategy);
                    put("Landsat8", l8Strategy);
                }});
    }
}
