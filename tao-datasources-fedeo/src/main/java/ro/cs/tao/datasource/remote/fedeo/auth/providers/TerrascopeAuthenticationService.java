package ro.cs.tao.datasource.remote.fedeo.auth.providers;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.cookie.Cookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.fedeo.auth.FedEOAuthenticationServiceProvider;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TerrascopeAuthenticationService extends FedEOAuthenticationServiceProvider {

    private static final String AUTH_URL = "https://sso.terrascope.be/auth/realms/terrascope/login-actions/authenticate";
    private static final String AUTH_TOKEN_NAME = "KEYCLOAK_IDENTITY";

    private static final List<String> protectedDomains = List.of(new String[]{
            "services.terrascope.be"
    });

    private static String extractAuthTokenFromCookies(String protectedURL, Credentials credentials) throws IOException {
        final List<Cookie> authCookies = NetUtils.getCookies(protectedURL, credentials);
        if (authCookies != null && !authCookies.isEmpty()) {
            final StringBuilder authCookieToken = new StringBuilder();
            for (Cookie authCookie : authCookies) {
                authCookieToken.append(authCookie.getName()).append("=").append(authCookie.getValue()).append(";");
            }
            return authCookieToken.toString();
        }
        return "";
    }

    private static String getAuthToken(String authUrl, UsernamePasswordCredentials credentials) throws IOException {
        final List<NameValuePair> loginParameters = new ArrayList<>();
        loginParameters.add(new BasicNameValuePair("username", credentials.getUserName()));
        loginParameters.add(new BasicNameValuePair("password", credentials.getPassword()));
        try (CloseableHttpResponse loginResponse = NetUtils.openConnection(HttpMethod.POST, authUrl, credentials, loginParameters, 30000)) {
            if (loginResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                final String authCookieToken = extractAuthTokenFromCookies(AUTH_URL, credentials);
                if (authCookieToken.contains(AUTH_TOKEN_NAME)) {
                    return authCookieToken;
                }
            }
            throw new IllegalStateException("401: Authentication failed.");
        }
    }

    @Override
    public List<String> getProtectedDomains() {
        return protectedDomains;
    }

    @Override
    protected String getAuthenticationTokenValue(String protectedURL, UsernamePasswordCredentials credentials) throws IOException {
        try (CloseableHttpResponse downloadRequestResponse = NetUtils.openConnection(HttpMethod.GET, protectedURL, credentials)) {
            if (downloadRequestResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new QueryException(String.format("The request was not successful. Reason: response code: %d: response message: %s", downloadRequestResponse.getStatusLine().getStatusCode(), downloadRequestResponse.getStatusLine().getReasonPhrase()));
            }
            if (downloadRequestResponse.getFirstHeader("Accept-Ranges") != null) {
                return extractAuthTokenFromCookies(protectedURL, credentials);
            }
            final String downloadRequestResponseContent = EntityUtils.toString(downloadRequestResponse.getEntity());
            final String newProtectedURL = downloadRequestResponseContent.replaceAll("[\\s\\S]*href=\"(.+?)\">Terrascope[\\s\\S]*", "$1").replaceAll("&amp;", "&");
            final String authUrl = getAuthUrl(newProtectedURL, credentials);
            if (!authUrl.startsWith(AUTH_URL)) {
                throw new IllegalStateException("Fail authentication for:" + protectedURL);
            }
            return getAuthToken(authUrl, credentials);
        }
    }

    protected String getAuthUrl(String downloadURL, UsernamePasswordCredentials credentials) throws IOException {
        try (CloseableHttpResponse downloadRequestResponse = NetUtils.openConnection(HttpMethod.GET, downloadURL, credentials)) {
            if (downloadRequestResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new QueryException(String.format("The request was not successful. Reason: response code: %d: response message: %s", downloadRequestResponse.getStatusLine().getStatusCode(), downloadRequestResponse.getStatusLine().getReasonPhrase()));
            }
            if (downloadRequestResponse.getFirstHeader("Accept-Ranges") != null) {
                return null;
            }
            final String downloadRequestResponseContent = EntityUtils.toString(downloadRequestResponse.getEntity());
            return downloadRequestResponseContent.replaceAll("[\\s\\S]*?action=\"(.+?)\".*?method=.post.[\\s\\S]*", "$1").replaceAll("&amp;", "&");
        }
    }
}
