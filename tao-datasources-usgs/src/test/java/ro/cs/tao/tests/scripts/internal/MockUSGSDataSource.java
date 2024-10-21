package ro.cs.tao.tests.scripts.internal;

import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.datasource.usgs.USGSDataSource;
import ro.cs.tao.datasource.usgs.USGSQuery;
import ro.cs.tao.datasource.usgs.json.handlers.LoginResponseHandler;
import ro.cs.tao.datasource.usgs.json.requests.LoginTokenRequest;
import ro.cs.tao.datasource.usgs.json.responses.LoginResponse;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Properties;

/**
 * @author Adrian Draghici
 */
public class MockUSGSDataSource extends URLDataSource<USGSQuery, String> {

    private static String URL;

    static {
        final Properties props = new Properties();
        try {
            props.load(USGSDataSource.class.getResourceAsStream("usgs.properties"));
            URL = props.getProperty("usgs.base.url");
            if (!URL.endsWith("/")) {
                URL += "/";
            }
        } catch (IOException ignored) {
        }
    }

    public MockUSGSDataSource() throws URISyntaxException {
        super(URL);
        final MockDatasetSearchParameterProvider parameterProvider = new MockUSGSParameterProvider(URL) {
            @Override
            public String authenticate() {
                return MockUSGSDataSource.this.authenticate();
            }
        };
        setParameterProvider(parameterProvider);
    }

    @Override
    public void setCredentials(String username, String password) {
        super.setCredentials(username, password);
    }

    @Override
    public String authenticate() {
        final LoginTokenRequest request = new LoginTokenRequest();
        if (credentials == null) {
            throw new QueryException(String.format("Credentials not set for %s", getId()));
        }
        request.setUsername(credentials.getUserName());
        request.setToken(credentials.getPassword());
        final String url = getConnectionString() + "login-token";
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, url, null, request.toString())) {
            return switch (response.getStatusLine().getStatusCode()) {
                case 200 -> {
                    final String body = EntityUtils.toString(response.getEntity());
                    yield extractApiKey(body);
                }
                case 401 -> throw new QueryException("Cannot retrieve API key [401:not authorized]");
                default -> throw new QueryException(String.format("The request was not successful. Reason: %s",
                        response.getStatusLine().getReasonPhrase()));
            };
        } catch (Exception ex) {
            throw new QueryException(ex);
        }
    }

    private static String extractApiKey(String body) {
        if (body == null) {
            throw new QueryException("Cannot retrieve API key [empty response body]");
        }
        final LoginResponse login = new JsonResponseParser<>(new LoginResponseHandler()).parseValue(body);
        if (login == null) {
            throw new QueryException("Cannot retrieve API key [empty response body]");
        }
        if (login.getErrorCode() == null) {
            final String apiKey = login.getData();
            if (apiKey == null) {
                throw new QueryException(String.format("The API key could not be obtained [requestId:%s,apiVersion:%s,errorCode:%s,error:%s,data:%s",
                        login.getSessionId(), login.getVersion(), login.getErrorCode(), login.getErrorMessage(), login.getData()));
            }
            return apiKey;
        } else {
            throw new QueryException("Cannot retrieve API key [403:AUTH_INVALID]");
        }
    }

    @Override
    protected USGSQuery createQueryImpl(String sensorName) {
        return null;
    }


    @Override
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        return super.getSupportedParameters();
    }

}
