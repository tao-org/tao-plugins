package ro.cs.tao.datasource.remote.earthdata;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.earthdata.parameters.EarthDataParameterProvider;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

public class EarthDataSource extends URLDataSource<EarthDataQuery, String> {

    private static final Properties props;
    private static String SEARCH_URL;
    private static String GEDI_DOWNLOAD_URL;

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
    public boolean ping() {
        return NetUtils.isAvailable(SEARCH_URL, credentials.getUserName(), credentials.getPassword());
    }

    @Override
    public void setCredentials(String username, String password){
        super.setCredentials(username, password);
    }

    @Override
    public String authenticate() throws IOException {
        if (credentials == null) {
            throw new IOException("No credentials set");
        }
        return NetUtils.getAuthToken(credentials.getUserName(), credentials.getPassword());
    }

    @Override
    protected EarthDataQuery createQueryImpl(String sensorName) {
        return new EarthDataQuery(this, sensorName);
    }
}
