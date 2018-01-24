package ro.cs.tao.datasource.remote.usgs.download;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.usgs.USGSDataSource;
import ro.cs.tao.datasource.util.HttpMethod;
import ro.cs.tao.datasource.util.NetUtils;
import ro.cs.tao.eodata.EOProduct;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.MissingResourceException;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class LandsatDownloadStrategy extends DownloadStrategy {
    private static final Properties properties;
    private static final String LOGIN_REQUEST = "login?jsonRequest={\"username\":\"%s\",\"password\":\"%s.\",\"authType\":\"EROS\",\"catalogId\":\"EE\"}";

    static {
        properties = new Properties();
        try {
            properties.load(USGSDataSource.class.getResourceAsStream("usgs.properties"));
        } catch (IOException ignored) {
        }
    }

    public LandsatDownloadStrategy(String targetFolder) {
        super(targetFolder, properties);
    }

    @Override
    protected String getMetadataUrl(EOProduct descriptor) {
        throw new RuntimeException("Metadata file not supported for this strategy");
    }

    @Override
    public Path fetch(EOProduct product) throws IOException, InterruptedException {
        String authToken = doAuthenticate();
        throw new IOException("Download not implemented in this data source");
    }

    private String doAuthenticate() throws IOException {
        String authUrl = properties.getProperty("usgs.base.url");
        if (authUrl == null) {
            throw new MissingResourceException("Authentication url not configured",
                                               USGSDataSource.class.getSimpleName(),
                                               "usgs.base.url");
        }
        authUrl += String.format(LOGIN_REQUEST, this.credentials.getUserName(), this.credentials.getPassword());
        String token = null;
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, authUrl, null)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(response.getEntity());
                    final JsonReader jsonReader = Json.createReader(new StringReader(body));
                    JsonObject responseObj = jsonReader.readObject();
                    token = responseObj.getString("data");
                    break;
                case 401:
                    throw new QueryException("The supplied credentials are invalid!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                                                           response.getStatusLine().getReasonPhrase()));
            }
        }
        return token;
    }
}
