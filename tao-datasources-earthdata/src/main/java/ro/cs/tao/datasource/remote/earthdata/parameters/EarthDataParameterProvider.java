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
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("SMAP-SPL3SMP", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL3SMP_E", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL4SMGP", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-NSIDC-0738", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL1BTB", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL1CTB", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL1CTB_E", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL2SMA", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL2SMAP", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL2SMAP_S", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL2SMP", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL2SMP_E", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL3SMA", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL3SMAP", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL4CMDL", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL4SMAU", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("SMAP-SPL4SMLM", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("IceSAT-2-ATL08V3", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("IceSAT-2-ATL08V2", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("IceSAT-2-ATL13V3", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("IceSAT-2-ATL13V2", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                    put("GEDI", new EarthDataDownloadStrategy(dataSource, targetFolder, properties));
                }});
    }
}
