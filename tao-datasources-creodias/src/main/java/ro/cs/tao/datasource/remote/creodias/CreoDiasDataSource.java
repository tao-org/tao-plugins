package ro.cs.tao.datasource.remote.creodias;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.creodias.landsat8.Landsat8Query;
import ro.cs.tao.datasource.remote.creodias.parameters.CreodiasParameterProvider;
import ro.cs.tao.datasource.remote.creodias.sentinel1.Sentinel1Query;
import ro.cs.tao.datasource.remote.creodias.sentinel2.Sentinel2Query;
import ro.cs.tao.datasource.util.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * Created by vnetoiu on 10/22/2019.
 */
public class CreoDiasDataSource extends URLDataSource<BaseDataQuery> {

    protected static final Properties properties;
    protected String sentinel1ConnectionString;
    protected String sentinel2ConnectionString;
    protected String landsat8ConnectionString;

    static {
        properties = new Properties();
        try {
            properties.load(CreoDiasDataSource.class.getResourceAsStream("creodias.properties"));
        } catch (IOException ignored) {
        }
    }

    public CreoDiasDataSource() throws URISyntaxException {
        super("");
        this.sentinel1ConnectionString = normalizedURL("s1.search.url");
        this.sentinel2ConnectionString = normalizedURL("s2.search.url");
        this.landsat8ConnectionString = normalizedURL("l8.search.url");
        setParameterProvider(new CreodiasParameterProvider());
    }

    @Override
    public String defaultId() { return "Creo DIAS"; }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(this.sentinel1ConnectionString) &&
                NetUtils.isAvailable(this.sentinel2ConnectionString) &&
                NetUtils.isAvailable(this.landsat8ConnectionString);
    }

    @Override
    protected BaseDataQuery createQueryImpl(String sensorName) {
        BaseDataQuery query;
        switch (sensorName) {
            case "Sentinel1":
                query = new Sentinel1Query(this, sensorName, sentinel1ConnectionString);
                break;
            case "Sentinel2":
                query = new Sentinel2Query(this, sensorName, sentinel2ConnectionString);
                break;
            case "Landsat8":
                query = new Landsat8Query(this, sensorName, landsat8ConnectionString);
                break;
            default:
                query = null;
        }
        return query;
    }

    private String normalizedURL(String urlPropertyName) {
        String url = properties.getProperty(urlPropertyName);
        if (!url.endsWith("/")) {
            url.concat("/");
        }
        return url;
    }
}
