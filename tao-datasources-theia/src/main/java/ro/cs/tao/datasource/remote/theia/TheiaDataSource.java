package ro.cs.tao.datasource.remote.theia;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.theia.parameters.TheiaParameterProvider;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TheiaDataSource extends URLDataSource<TheiaDataQuery, Header> {
    private static final Properties props;
    private static String LOGIN_URL;
    static String SEARCH_URL;
    static String DOWNLOAD_URL;
    private Header authHeader;

    static {
        props = new Properties();
        try {
            props.load(TheiaDataSource.class.getResourceAsStream("theia.properties"));
            LOGIN_URL = props.getProperty("theia.login.url");
            SEARCH_URL = props.getProperty("theia.search.url");
            DOWNLOAD_URL = props.getProperty("theia.download.url");
        } catch (IOException ignored) {
        }
    }

    public TheiaDataSource() throws URISyntaxException {
        super(LOGIN_URL);
        setParameterProvider(new TheiaParameterProvider(this));
        this.properties = props;
    }

    @Override
    public String defaultId() { return "THEIA"; }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public void setCredentials(String username, String password) {
        super.setCredentials(username, password);
    }

    @Override
    public Header authenticate() {
        if (this.authHeader == null) {
            if (this.credentials == null) {
                throw new QueryException(String.format("Credentials not set for %s", getId()));
            }
            final List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("ident", credentials.getUserName()));
            params.add(new BasicNameValuePair("pass", credentials.getPassword()));
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, getConnectionString(), (Credentials)null, params)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        String token = EntityUtils.toString(response.getEntity());
                        if (token == null) {
                            throw new QueryException("Cannot retrieve API key [empty response body]");
                        }
                        this.authHeader = new BasicHeader("Authorization", "Bearer " + token);
                        break;
                    case 401:
                        throw new QueryException("Cannot retrieve API key [401:not authorized]");
                    default:
                        throw new QueryException(String.format("The request was not successful. Reason: %s",
                                response.getStatusLine().getReasonPhrase()));
                }
            } catch (Exception ex) {
                throw new QueryException(ex);
            }
        }
        return this.authHeader;
    }

    @Override
    protected TheiaDataQuery createQueryImpl(String sensorName) {
        return new TheiaDataQuery(this, sensorName);
    }
}
