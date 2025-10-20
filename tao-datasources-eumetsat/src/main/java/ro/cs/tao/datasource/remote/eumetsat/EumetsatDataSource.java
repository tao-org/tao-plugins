package ro.cs.tao.datasource.remote.eumetsat;

import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.component.enums.AuthenticationType;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.eumetsat.parameters.EumetsatParameterProvider;
import ro.cs.tao.opensearch.OpenSearchClient;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

public class EumetsatDataSource extends URLDataSource<EumetsatDataQuery, Void> {

    private static String authUrl;
    private static String searchUrl;

    static {
        Properties props = new Properties();
        try {
            props.load(EumetsatDataSource.class.getResourceAsStream("eumetsat.properties"));
            authUrl = props.getProperty("token.endpoint");
            searchUrl = props.getProperty("search.endpoint");
        } catch (IOException ignored) {
        }
    }

    private OpenSearchClient client;

    public EumetsatDataSource() throws URISyntaxException {
        this(searchUrl);
    }

    public EumetsatDataSource(String url) throws URISyntaxException {
        super(url);
        setParameterProvider(new EumetsatParameterProvider(this));
        this.client = new OpenSearchClient(searchUrl);
    }

    @Override
    public String defaultId() {
        return "EUMETSAT";
    }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(searchUrl, "", "");
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public Void authenticate() throws IOException {
        if (credentials == null || this.client == null) {
            throw new IOException("No credentials set");
        }
        WebServiceAuthentication auth = new WebServiceAuthentication();
        auth.setType(AuthenticationType.TOKEN);
        auth.setUser(this.credentials.getUserName());
        auth.setPassword(this.credentials.getPassword());
        auth.setLoginUrl(authUrl);
        auth.setAuthHeader("Authorization");
        this.client.setAuthentication(auth);
        return null;
    }

    public OpenSearchClient getClient() {
        return client;
    }

    @Override
    protected EumetsatDataQuery createQueryImpl(String sensorName) {
        return new EumetsatDataQuery(this, sensorName);
    }
}
