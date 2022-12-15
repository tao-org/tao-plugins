package ro.cs.tao.execution.wps;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.geotools.http.HTTPResponse;
import org.geotools.http.SimpleHttpClient;
import org.geotools.util.Base64;
import org.geotools.util.factory.GeoTools;
import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.services.commons.ServiceResponse;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.logging.Logger;

public class WPSHttpClient extends SimpleHttpClient {
    private final Logger logger = Logger.getLogger(WPSHttpClient.class.getName());
    private final WebServiceAuthentication authentication;

    public WPSHttpClient(WebServiceAuthentication authentication) {
        super();
        this.authentication = authentication;
        if (this.authentication != null) {
            setUser(this.authentication.getUser());
            setPassword(this.authentication.getPassword());
        }
    }

    @Override
    public HTTPResponse get(URL url, Map<String, String> headers) throws IOException {
        logger.finest("URL is " + url);

        URLConnection connection = openAuthConnection(url);
        if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection) connection).setRequestMethod("GET");
        }

        // Set User-Agent to a good default
        connection.addRequestProperty("User-Agent", "GeoTools HTTPClient (" + GeoTools.getVersion() + ")");
        if (headers != null) {
            for (Map.Entry<String, String> headerNameValue : headers.entrySet()) {
                connection.addRequestProperty(headerNameValue.getKey(), headerNameValue.getValue());
            }
        }

        connection.connect();

        return new SimpleHTTPResponse(connection);
    }

    @Override
    public HTTPResponse post(URL url, InputStream postContent, String postContentType) throws IOException {
        URLConnection connection = openAuthConnection(url);
        if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection) connection).setRequestMethod("POST");
        }
        connection.setDoOutput(true);
        if (postContentType != null) {
            connection.setRequestProperty("Content-type", postContentType);
        }

        connection.connect();

        try (OutputStream outputStream = connection.getOutputStream()) {
            byte[] buff = new byte[512];
            int count;
            while ((count = postContent.read(buff)) > -1) {
                outputStream.write(buff, 0, count);
            }
            outputStream.flush();
        }

        return new SimpleHTTPResponse(connection);
    }

    private URLConnection openConnection(URL finalURL) throws IOException {
        URLConnection connection = finalURL.openConnection();
        final boolean http = connection instanceof HttpURLConnection;
        // mind, connect timeout is in seconds
        if (http && getConnectTimeout() > 0) {
            connection.setConnectTimeout(1000 * getConnectTimeout());
        }
        if (http && getReadTimeout() > 0) {
            connection.setReadTimeout(1000 * getReadTimeout());
        }
        if (http && finalURL.getProtocol().equalsIgnoreCase("https")) {
            try {
                applySelfSignedLocalhostFix((HttpsURLConnection) connection);
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new IOException(e);
            }
        }
        return connection;
    }

    private URLConnection openAuthConnection(URL finalURL) throws IOException {
        URLConnection connection = openConnection(finalURL);
        final String username = getUser();
        final String password = getPassword();

        if (connection instanceof HttpURLConnection && username != null && password != null) {
            switch (this.authentication.getType()) {
                case BASIC:
                    String userpassword = username + ":" + password;
                    String encodedAuthorization =
                            Base64.encodeBytes(
                                    userpassword.getBytes(StandardCharsets.UTF_8), Base64.DONT_BREAK_LINES);
                    connection.setRequestProperty("Authorization", "Basic " + encodedAuthorization);
                    break;
                case TOKEN:
                    connection.setRequestProperty(this.authentication.getAuthHeader(), doLogin());
                    break;
            }
        }
        return connection;
    }

    private String doLogin() throws IOException {
        if (this.authentication != null) {
            URLConnection connection = openConnection(new URL(this.authentication.getLoginUrl()));
            ((HttpURLConnection) connection).setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
            try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
                final String content = "user=" + URLEncoder.encode(getUser(), "UTF-8") + "&password=" + URLEncoder.encode(getPassword(), "UTF-8");
                out.writeBytes(content);
                out.flush();
            }
            final StringBuilder builder = new StringBuilder();
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            final ServiceResponse response = new ObjectMapper().readerFor(ServiceResponse.class).readValue(builder.toString());
            final Object data = response.getData();
            if (data instanceof String) {
                return (String) data;
            } else {
                final Map.Entry<String, String> entry = ((Map<String, String>) data).entrySet().stream()
                                                                                    .filter(e -> e.getKey().toLowerCase().contains("token"))
                                                                                    .findFirst().orElse(null);
                return entry != null ? entry.getValue() : null;
            }
        } else {
            return null;
        }
    }

    private HttpsURLConnection applySelfSignedLocalhostFix(HttpsURLConnection connection) throws NoSuchAlgorithmException, KeyManagementException {
        final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public void checkClientTrusted(X509Certificate[] certs, String authType) {  }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {  }

                }
        };

        final SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        connection.setSSLSocketFactory(sc.getSocketFactory());

        // Install the all-trusting host verifier
        connection.setHostnameVerifier((hostname, session) -> true);
        /*
         * end of the fix
         */
        return  connection;
    }
}