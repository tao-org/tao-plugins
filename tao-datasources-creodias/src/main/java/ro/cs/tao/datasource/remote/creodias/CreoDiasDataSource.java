package ro.cs.tao.datasource.remote.creodias;

import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.creodias.model.common.Token;
import ro.cs.tao.datasource.remote.creodias.parameters.CreodiasParameterProvider;
import ro.cs.tao.datasource.remote.creodias.parsers.LoginResponseHandler;
import ro.cs.tao.datasource.remote.creodias.queries.*;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by vnetoiu on 10/22/2019.
 */
public class CreoDiasDataSource extends URLDataSource<BaseDataQuery, Token> {

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
            //put("Sentinel1", normalizedURL("s1.search.url"));
            put("Sentinel1", properties.getProperty("s1.search.url"));
            //put("Sentinel2", normalizedURL("s2.search.url"));
            put("Sentinel2", properties.getProperty("s2.search.url"));
            //put("Landsat8", normalizedURL("l8.search.url"));
            put("Sentinel3", properties.getProperty("s3.search.url"));
            put("Sentinel5P", properties.getProperty("s5p.search.url"));
            put("Landsat8", properties.getProperty("l8.search.url"));
            put("Login", properties.getProperty("login.url"));
            put("Download", properties.getProperty("download.url"));
        }};
        setParameterProvider(new CreodiasParameterProvider(this));
    }

    @Override
    public String defaultId() { return "Creo DIAS"; }

    @Override
    public boolean requiresAuthentication() { return true; }

    @Override
    public Token authenticate() {
        if (credentials == null) {
            throw new QueryException(String.format("Credentials not set for %s", getId()));
        }
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", "CLOUDFERRO_PUBLIC"));
        params.add(new BasicNameValuePair("username", credentials.getUserName()));
        params.add(new BasicNameValuePair("password", credentials.getPassword()));
        params.add(new BasicNameValuePair("grant_type", "password"));
        final String url = getConnectionString("Login");
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, url, (Credentials) null, params)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(response.getEntity());
                    if (body == null) {
                        throw new QueryException("Cannot retrieve API key [empty response body]");
                    }
                    ResponseParser<Token> parser = new JsonResponseParser<>(new LoginResponseHandler());
                    final Token token = parser.parseValue(body);
                    if (token != null) {
                        return token;
                    } else {
                        throw new QueryException(String.format("Cannot retrieve API key [received: %s]", body));
                    }
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
            case "Sentinel3":
                query = new Sentinel3Query(this, sensorName, this.connectionStrings.get(sensorName));
                break;
            case "Sentinel5P":
                query = new Sentinel5PQuery(this, sensorName, this.connectionStrings.get(sensorName));
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
