package ro.cs.tao.datasource.remote.creodias;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.creodias.landsat8.Landsat8Query;
import ro.cs.tao.datasource.remote.creodias.parameters.CreodiasParameterProvider;
import ro.cs.tao.datasource.remote.creodias.sentinel1.Sentinel1Query;
import ro.cs.tao.datasource.remote.creodias.sentinel2.Sentinel2Query;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by vnetoiu on 10/22/2019.
 */
public class CreoDiasDataSource extends URLDataSource<BaseDataQuery> {

    protected static final Properties properties;
    protected Map<String, String> connectionStrings;
    /*protected String sentinel1ConnectionString;
    protected String sentinel2ConnectionString;
    protected String landsat8ConnectionString;*/

    static {
        properties = new Properties();
        try {
            properties.load(CreoDiasDataSource.class.getResourceAsStream("creodias.properties"));
        } catch (IOException ignored) {
        }
    }

    public CreoDiasDataSource() throws URISyntaxException {
        super("");
        this.connectionStrings = new HashMap<String, String>() {{
            put("Sentinel1", normalizedURL("s1.search.url"));
            put("Sentinel2", normalizedURL("s2.search.url"));
            put("Landsat8", normalizedURL("l8.search.url"));
        }};
        setParameterProvider(new CreodiasParameterProvider(this));
    }

    @Override
    public String defaultId() { return "Creo DIAS"; }

    @Override
    public boolean ping() {
        for (String value : this.connectionStrings.values()) {
            if (!NetUtils.isAvailable(value, credentials.getUserName(), credentials.getPassword())) {
                return false;
            }
        }
        return true;
        /*return NetUtils.isAvailable(this.sentinel1ConnectionString, credentials.getUserName(), credentials.getPassword()) &&
                NetUtils.isAvailable(this.sentinel2ConnectionString, credentials.getUserName(), credentials.getPassword()) &&
                NetUtils.isAvailable(this.landsat8ConnectionString, credentials.getUserName(), credentials.getPassword());*/
    }

    @Override
    public String getConnectionString(String sensorName) {
        return this.connectionStrings.get(sensorName);
    }

    @Override
    protected BaseDataQuery createQueryImpl(String sensorName) {
        BaseDataQuery query;
        switch (sensorName) {
            case "Sentinel1":
                query = new Sentinel1Query(this, sensorName, this.connectionStrings.get(sensorName));
                break;
            case "Sentinel2":
                query = new Sentinel2Query(this, sensorName, this.connectionStrings.get(sensorName));
                break;
            case "Landsat8":
                query = new Landsat8Query(this, sensorName, this.connectionStrings.get(sensorName));
                break;
            default:
                query = null;
        }
        return query;
    }

    private String normalizedURL(String urlPropertyName) {
        String url = properties.getProperty(urlPropertyName);
        if (!url.endsWith("/")) {
            url = url.concat("/");
        }
        return url;
    }
}
