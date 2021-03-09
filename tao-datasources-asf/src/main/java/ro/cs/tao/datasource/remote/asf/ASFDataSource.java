package ro.cs.tao.datasource.remote.asf;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.asf.parameters.ASFParameterProvider;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

public class ASFDataSource extends URLDataSource<ASFQuery, String> {
    private static final Properties props;
    private static String URL;

    static {
        props = new Properties();
        try {
            props.load(ASFDataSource.class.getResourceAsStream("asf.properties"));
            URL = props.getProperty("asf.search.url");
        } catch (IOException ignored) {
        }
    }

    public ASFDataSource() throws URISyntaxException {
        super(URL);
        setParameterProvider(new ASFParameterProvider(this));
        this.properties = ASFDataSource.props;
    }

    @Override
    public String defaultId() { return "Alaska Satellite Facility"; }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(URL, credentials.getUserName(), credentials.getPassword());
    }

    @Override
    public boolean requiresAuthentication() { return true; }

    @Override
    public void setCredentials(String username, String password) {
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
    protected ASFQuery createQueryImpl(String sensorName) {
        return new ASFQuery(this, sensorName);
    }
}

