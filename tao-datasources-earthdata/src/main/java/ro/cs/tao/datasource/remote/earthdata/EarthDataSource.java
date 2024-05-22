package ro.cs.tao.datasource.remote.earthdata;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.earthdata.parameters.EarthDataParameterProvider;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URISyntaxException;
import java.util.Properties;

public class EarthDataSource extends URLDataSource<EarthDataQuery, String> {

    private static final Properties props;
    private static String SEARCH_URL;
    private static String GEDI_DOWNLOAD_URL;
    private String bearerToken;

    static {
        props = new Properties();
        try {
            props.load(EarthDataSource.class.getResourceAsStream("earthdata.properties"));
            SEARCH_URL = props.getProperty("earthdata.search.url");
        } catch (IOException ignored) {
        }
    }

    public EarthDataSource() throws URISyntaxException {
        super(SEARCH_URL);
        setParameterProvider(new EarthDataParameterProvider(this));
        this.properties = EarthDataSource.props;
    }

    @Override
    public String defaultId() {
        return "EarthData";
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public boolean isBearerTokenSupported() {
        return true;
    }

    @Override
    public String getBearerToken() {
        return bearerToken;
    }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(SEARCH_URL, credentials.getUserName(), credentials.getPassword());
    }

    @Override
    public void setCredentials(String username, String password){
        super.setCredentials(username, password);
    }

    @Override
    public void setBearerToken(String token) {
        this.bearerToken = token;
    }

    @Override
    public String authenticate() throws IOException {
        if (credentials == null) {
            throw new IOException("No credentials set");
        }
        // First set the default cookie manager.
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        return NetUtils.getAuthToken(credentials.getUserName(), credentials.getPassword());
    }

    @Override
    protected EarthDataQuery createQueryImpl(String sensorName) {
        return new EarthDataQuery(this, sensorName);
    }
}
