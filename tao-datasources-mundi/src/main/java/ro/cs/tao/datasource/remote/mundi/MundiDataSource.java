package ro.cs.tao.datasource.remote.mundi;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.mundi.landsat8.Landsat8Query;
import ro.cs.tao.datasource.remote.mundi.parameters.MundiParameterProvider;
import ro.cs.tao.datasource.remote.mundi.sentinel1.Sentinel1Query;
import ro.cs.tao.datasource.remote.mundi.sentinel2.Sentinel2Query;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by vnetoiu on 10/22/2019.
 */
public class MundiDataSource extends URLDataSource<BaseDataQuery, Header> {

    protected static final Properties properties;
    protected Map<String, String> connectionStrings;
    private static String LOGIN_URL;
    private static String AUTH_URL;
    private Header authHeader;

    static {
        properties = new Properties();
        try {
            properties.load(MundiDataSource.class.getResourceAsStream("mundi.properties"));
            LOGIN_URL = properties.getProperty("login.url");
            AUTH_URL = properties.getProperty("auth.url");
        } catch (IOException ignored) {
        }
    }

    public MundiDataSource() throws URISyntaxException {
        super("");
        this.connectionStrings = new HashMap<String, String>() {{
            put("Sentinel1",  properties.getProperty("s1.search.url"));
            put("Sentinel2",  properties.getProperty("s2.search.url"));
            put("Landsat8",  properties.getProperty("l8.search.url"));
        }};
        setParameterProvider(new MundiParameterProvider(this));
    }

    public Properties getProperties() { return properties; }

    @Override
    public int getMaximumAllowedTransfers() { return 4; }

    @Override
    public void setCredentials(String username, String password) {
        super.setCredentials(username, password);
    }

    @Override
    public String defaultId() { return "Mundi DIAS"; }

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

    public Header authenticate() throws QueryException {
        if (this.authHeader == null) {
            try {
                String sessionDataKey;
                String newUrl;
                final CookieManager cookieManager = new CookieManager();
                HttpURLConnection connection = NetUtils.openConnection(LOGIN_URL);
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                final Map<String, List<String>> headerFields = connection.getHeaderFields();
                final List<String> cookies = headerFields.get("Set-Cookie");
                if (cookies != null) {
                    for (String cookie : cookies) {
                        cookieManager.getCookieStore().add(null, HttpCookie.parse(cookie).get(0));
                    }
                }
                final InputStream stream = connection.getInputStream();
                int read;
                byte[] buffer = new byte[4096];
                final StringBuilder builder = new StringBuilder();
                while ((read = stream.read(buffer)) != -1) {
                    builder.append(new String(Arrays.copyOfRange(buffer, 0, read)));
                }
                stream.close();
                newUrl = connection.getURL().toString();
                final String contents = builder.toString();
                int first = contents.indexOf("'", contents.indexOf("name=\"sessionDataKey\"")) + 1;
                int last = contents.indexOf("'", first);
                sessionDataKey = contents.substring(first, last);

                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("username", this.credentials.getUserName()));
                params.add(new BasicNameValuePair("password", this.credentials.getPassword()));
                params.add(new BasicNameValuePair("sessionDataKey", sessionDataKey));
                final List<HttpCookie> cookies2 = cookieManager.getCookieStore().getCookies();
                List<Header> headers = new ArrayList<>();
                headers.add(new BasicHeader("Host", "identity.mundiwebservices.com"));
                headers.add(new BasicHeader("Origin", "https://identity.mundiwebservices.com"));
                headers.add(new BasicHeader("Referer", newUrl));
                if (cookies != null && cookies.size() > 0) {
                    headers.add(new BasicHeader("Cookie", cookies2.stream().map(c -> c.getName() + "=" + c.getValue()).collect(Collectors.joining(";"))));
                }
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, AUTH_URL, headers, params)) {
                    final HeaderIterator iterator = response.headerIterator();
                    while (iterator.hasNext()) {
                        final Header header = iterator.nextHeader();
                        if (header.getName().equals("Set-Cookie")) {
                            final String value = header.getValue();
                            if (value.startsWith("commonAuthId")) {
                                int firstIdx = value.indexOf('=') + 1;
                                this.authHeader = new BasicHeader("Cookie", "seeedtoken=" + value.substring(firstIdx, value.indexOf(';', firstIdx)));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new QueryException(e);
            }
        }
        return this.authHeader;
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
}
