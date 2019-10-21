package ro.cs.tao.datasource.remote.asf.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.SimpleArchiveDownloadStrategy;

import java.util.*;

public class ASFParameterProvider extends AbstractParameterProvider {

    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() {
        return Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1",
                        new SimpleArchiveDownloadStrategy(ConfigurationManager.getInstance().getValue("product.location"),
                                                          new Properties() {{ put("auto.uncompress", "false"); }}));
                }});
    }
}
