package ro.cs.tao.datasource.remote.fedeo.auth.providers;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicHeader;
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

public class JaxaAuthenticationService extends FedEOAuthenticationServiceProvider {

    private static final String AUTH_URL = "https://gportal.jaxa.jp/gpr/auth/authenticate.json";
    private static final String ORIGIN_AUTH_URL = "https://gportal.jaxa.jp";
    private static final String AUTH_TOKEN_NAME = "iPlanetDirectoryPro";
    private static final String LOGIN_TOKEN_NAME = "fuel_csrf_token";

    private static final List<String> protectedDomains = List.of(new String[]{
            "gportal.jaxa.jp"
    });

    private static String extractAuthTokenFromCookies(String protectedURL, Credentials credentials) throws IOException {
        final List<Cookie> jaxaCookies = NetUtils.getCookies(protectedURL, credentials);
        if (jaxaCookies != null && !jaxaCookies.isEmpty()) {
            final StringBuilder authCookieToken = new StringBuilder();
            for (Cookie jaxaCookie : jaxaCookies) {
                authCookieToken.append(jaxaCookie.getName()).append("=").append(jaxaCookie.getValue()).append(";");
            }
            return authCookieToken.toString();
        }
        return "";
    }

    private static String getLoginToken(String protectedURL, Credentials credentials) throws IOException {
        final List<Cookie> jaxaCookies = NetUtils.getCookies(protectedURL, credentials);
        return jaxaCookies.stream().filter(jc -> jc.getName().equals(LOGIN_TOKEN_NAME)).findFirst().orElse(new BasicClientCookie("", "")).getValue();
    }

    private static String getAuthToken(String loginToken, UsernamePasswordCredentials credentials) throws IOException {
        final List<NameValuePair> loginParameters = new ArrayList<>();
        loginParameters.add(new BasicNameValuePair("account", credentials.getUserName()));
        loginParameters.add(new BasicNameValuePair("password", credentials.getPassword()));
        loginParameters.add(new BasicNameValuePair(LOGIN_TOKEN_NAME, loginToken));
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader("Origin", ORIGIN_AUTH_URL));
        try (CloseableHttpResponse loginResponse = NetUtils.openConnection(HttpMethod.POST, AUTH_URL, credentials, headers, loginParameters, 30000)) {
            if (loginResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK && !EntityUtils.toString(loginResponse.getEntity()).contains("\"key\":null")) {
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
            final String loginToken = getLoginToken(protectedURL, credentials);
            if (loginToken.isEmpty()) {
                throw new IllegalStateException("Fail authentication for:" + protectedURL);
            }
            return getAuthToken(loginToken, credentials);
        }
    }
}
