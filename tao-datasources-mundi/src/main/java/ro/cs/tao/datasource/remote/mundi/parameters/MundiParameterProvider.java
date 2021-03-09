package ro.cs.tao.datasource.remote.mundi.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.mundi.DownloadStrategy;
import ro.cs.tao.datasource.remote.mundi.MundiDataSource;
import ro.cs.tao.datasource.remote.mundi.landsat8.Landsat8Strategy;
import ro.cs.tao.datasource.remote.mundi.sentinel2.Sentinel2DownloadStrategy;

import java.util.Collections;
import java.util.HashMap;

/**
 * Created by vnetoiu on 10/22/2019.
 */
public class MundiParameterProvider extends AbstractParameterProvider {

    public MundiParameterProvider(MundiDataSource dataSource) {
        super();
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1", new DownloadStrategy(dataSource, targetFolder, dataSource.getProperties()));
                    put("Sentinel2", new Sentinel2DownloadStrategy(dataSource, targetFolder));
                    put("Landsat8", new Landsat8Strategy(dataSource, targetFolder));
                }});
    }
}
