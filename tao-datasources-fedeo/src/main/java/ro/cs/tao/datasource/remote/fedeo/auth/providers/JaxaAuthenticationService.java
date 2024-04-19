package ro.cs.tao.datasource.remote.fedeo.auth.providers;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;
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

    private static final String AUTH_URL="https://gportal.jaxa.jp/gpr/auth/authenticate.json";

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
        return jaxaCookies.stream().filter(jc -> jc.getName().equals("fuel_csrf_token")).findFirst().orElse(new BasicClientCookie("", "")).getValue();
    }

    private static String getAuthToken(String loginToken, UsernamePasswordCredentials credentials) throws IOException {
        final List<NameValuePair> loginParameters = new ArrayList<>();
        loginParameters.add(new BasicNameValuePair("account", credentials.getUserName()));
        loginParameters.add(new BasicNameValuePair("password", credentials.getPassword()));
        loginParameters.add(new BasicNameValuePair("fuel_csrf_token", loginToken));
        try (CloseableHttpResponse loginResponse = NetUtils.openConnection(HttpMethod.POST, AUTH_URL, credentials, loginParameters)) {
            if (loginResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK && !EntityUtils.toString(loginResponse.getEntity()).contains("\"key\":null")) {
                final String authCookieToken = extractAuthTokenFromCookies(AUTH_URL, credentials);
                if (authCookieToken.contains("iPlanetDirectoryPro")) {
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
            if (downloadRequestResponse.getFirstHeader("Content-Disposition") != null) {
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
