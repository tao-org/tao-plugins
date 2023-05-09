package ro.cs.tao.datasource.remote.lsa.auth;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LSAAuthentication {

    public static final String DOWNLOAD_URL_PROPERTY_NAME = "DOWNLOAD_URL";

    private static final String TOKEN_NAME = "Cookie";

    private final UsernamePasswordCredentials credentials;
    private String cookieToken = "";

    public LSAAuthentication(UsernamePasswordCredentials credentials) {
        this.credentials = credentials;
    }

    private String getKeycloakToken(String keycloakLoginAddress, String keyCloakLoginCookie) throws IOException {
        final List<NameValuePair> keycloakLoginParameters = new ArrayList<>();
        keycloakLoginParameters.add(new BasicNameValuePair("username", this.credentials.getUserName()));
        keycloakLoginParameters.add(new BasicNameValuePair("password", this.credentials.getPassword()));
        keycloakLoginParameters.add(new BasicNameValuePair("credentialId", ""));
        try (final CloseableHttpResponse keycloakLoginResponse = NetUtils.openConnection(HttpMethod.POST, keycloakLoginAddress, new BasicHeader(TOKEN_NAME, keyCloakLoginCookie), keycloakLoginParameters)) {
            final Header[] keycloakCookies = keycloakLoginResponse.getHeaders("Set-Cookie");
            if (keycloakCookies != null && keycloakCookies.length>0) {
                final StringBuilder keyCloakToken = new StringBuilder();
                for (final Header keycloakCookie : keycloakCookies) {
                    keyCloakToken.append(keycloakCookie.getValue(), 0, keycloakCookie.getValue().indexOf(';')).append(';');
                }
                return keyCloakToken.toString();
            }
            String errMsg = "Get keycloak cookie token failed. Reason: ";
            if (keycloakLoginResponse.getStatusLine().getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY) {
                errMsg = errMsg + "Status: " + keycloakLoginResponse.getStatusLine().getStatusCode();
            } else {
                errMsg = errMsg + keycloakLoginResponse.getFirstHeader("Location").getValue();
            }
            throw new IllegalStateException(errMsg);
        }
    }

    public String getAuthenticationTokenName() {
        return TOKEN_NAME;
    }

    public String getAuthenticationTokenValue(String protectedURL) throws IOException {
        final String currentCookieToken = this.cookieToken;
        int responseStatus;
        try (final CloseableHttpResponse downloadRequestResponse = NetUtils.openConnection(HttpMethod.GET, protectedURL, getAuthenticationTokenName(), currentCookieToken, null)) {
            responseStatus = downloadRequestResponse.getStatusLine().getStatusCode();
            if (responseStatus != HttpStatus.SC_OK) {
                throw new QueryException(String.format("The request was not successful. Reason: response code: %d: response message: %s", downloadRequestResponse.getStatusLine().getStatusCode(), downloadRequestResponse.getStatusLine().getReasonPhrase()));
            }
            if (downloadRequestResponse.getFirstHeader("Content-Type") != null && downloadRequestResponse.getFirstHeader("Content-Type").getValue().equals("application/zip")) {
                return currentCookieToken;
            }
            final String downloadRequestResponseBody = EntityUtils.toString(downloadRequestResponse.getEntity());
            final int idx = downloadRequestResponseBody.indexOf("action=\"") + 8;
            final String keycloakAuthUrl = downloadRequestResponseBody.substring(idx, downloadRequestResponseBody.indexOf('"', idx)).replaceAll("&amp;","&");
            final StringBuilder keyCloakLoginCookie = new StringBuilder();
            for (final Header downloadRequestResponseHeader : downloadRequestResponse.getHeaders("Set-Cookie")) {
                keyCloakLoginCookie.append(downloadRequestResponseHeader.getValue(), 0, downloadRequestResponseHeader.getValue().indexOf(';')).append(';');
            }
            this.cookieToken = getKeycloakToken(keycloakAuthUrl, keyCloakLoginCookie.toString());
            return this.cookieToken;
        }
    }
}
