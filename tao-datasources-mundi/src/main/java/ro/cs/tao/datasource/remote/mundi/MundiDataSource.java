package ro.cs.tao.datasource.remote.mundi;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.mundi.landsat8.Landsat8Query;
import ro.cs.tao.datasource.remote.mundi.parameters.MundiParameterProvider;
import ro.cs.tao.datasource.remote.mundi.sentinel1.Sentinel1Query;
import ro.cs.tao.datasource.remote.mundi.sentinel2.Sentinel2Query;
import ro.cs.tao.datasource.util.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * Created by vnetoiu on 10/22/2019.
 */
public class MundiDataSource extends URLDataSource<BaseDataQuery> {

    protected static final Properties properties;
    protected String sentinel1ConnectionString;
    protected String sentinel2ConnectionString;
    protected String landsat8ConnectionString;

    static {
        properties = new Properties();
        try {
            properties.load(MundiDataSource.class.getResourceAsStream("mundi.properties"));
        } catch (IOException ignored) {
        }
    }

    public MundiDataSource() throws URISyntaxException {
        super("");
        this.sentinel1ConnectionString = properties.getProperty("s1.search.url");
        this.sentinel2ConnectionString = properties.getProperty("s2.search.url");
        this.landsat8ConnectionString = properties.getProperty("l8.search.url");
        setParameterProvider(new MundiParameterProvider());
    }

    @Override
    public int getMaximumAllowedTransfers() { return 4; }

    @Override
    public void setCredentials(String username, String password) {
        //super.setCredentials(username, password);
    }

    @Override
    public String defaultId() { return "Mundi DIAS"; }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(this.sentinel1ConnectionString, credentials.getUserName(), credentials.getPassword()) &&
                NetUtils.isAvailable(this.sentinel2ConnectionString, credentials.getUserName(), credentials.getPassword()) &&
                NetUtils.isAvailable(this.landsat8ConnectionString, credentials.getUserName(), credentials.getPassword());
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
}
