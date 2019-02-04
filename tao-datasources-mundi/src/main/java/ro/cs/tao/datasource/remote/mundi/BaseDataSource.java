package ro.cs.tao.datasource.remote.mundi;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.mundi.landsat8.Landsat8Query;
import ro.cs.tao.datasource.remote.mundi.sentinel1.Sentinel1Query;
import ro.cs.tao.datasource.remote.mundi.sentinel2.Sentinel2Query;
import ro.cs.tao.datasource.util.NetUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

public abstract class BaseDataSource extends URLDataSource<BaseDataQuery> {

    protected static final Properties properties;

    static {
        properties = new Properties();
        try {
            properties.load(BaseDataSource.class.getResourceAsStream("mundi.properties"));
        } catch (IOException ignored) {
        }
    }

    public BaseDataSource() throws URISyntaxException {
        super("");
        this.connectionString = normalizedURL();
        this.remoteUrl = new URI(this.connectionString);
    }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(this.connectionString);
    }

    @Override
    protected BaseDataQuery createQueryImpl(String sensorName) {
        BaseDataQuery query;
        switch (sensorName) {
            case "Sentinel1":
                query = new Sentinel1Query(this);
                break;
            case "Sentinel2":
                query = new Sentinel2Query(this);
                break;
            case "Landsat8":
                query = new Landsat8Query(this);
                break;
            default:
                query = null;
        }
        return query;
    }

    public abstract String urlPropertyName();

    private String normalizedURL() {
        String url = properties.getProperty(urlPropertyName());
        if (!url.endsWith("/")) {
            url.concat("/");
        }
        return url;
    }
}