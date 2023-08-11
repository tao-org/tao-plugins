package ro.cs.tao.datasource.remote.eocat.auth;

import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.cookie.Cookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EOCATAuthentication {

    public static final String DOWNLOAD_URL_PROPERTY_NAME = "DOWNLOAD_URL";
    private static final String SAML_LOGIN_ADDRESS = "https://eoiam-idp.eo.esa.int/samlsso";

    private static final String TOKEN_NAME = "Cookie";

    private final UsernamePasswordCredentials credentials;
    private String cookieToken = "";

    public EOCATAuthentication(UsernamePasswordCredentials credentials) {
        this.credentials = credentials;
    }

    private static String getSAMLLoginResponse(String sessionDataKey, UsernamePasswordCredentials credentials) throws IOException {
        final List<NameValuePair> samlLoginParameters = new ArrayList<>();
        samlLoginParameters.add(new BasicNameValuePair("tocommonauth", "true"));
        samlLoginParameters.add(new BasicNameValuePair("username", credentials.getUserName()));
        samlLoginParameters.add(new BasicNameValuePair("password", credentials.getPassword()));
        samlLoginParameters.add(new BasicNameValuePair("sessionDataKey", sessionDataKey));
        try (CloseableHttpResponse samlLoginResponse = NetUtils.openConnection(HttpMethod.POST, SAML_LOGIN_ADDRESS, credentials, samlLoginParameters)) {
            final String samlLoginRequestResponseBody = EntityUtils.toString(samlLoginResponse.getEntity());
            if (samlLoginRequestResponseBody.isEmpty()) {
                throw new IllegalStateException("Get SAMLResponse failed.");
            }
            return samlLoginRequestResponseBody;
        }
    }

    private static String[] getSAMLData(String samlLoginResponse) {
        if (samlLoginResponse.contains("name='SAMLResponse'")) {
            final String samlLoginAddress = samlLoginResponse.replaceAll("[\\s\\S]*?method='post' action='(.*?)'[\\s\\S]*", "$1");
            final String samlResponse = samlLoginResponse.replaceAll("[\\s\\S]*?name='SAMLResponse' value='(.*?)'[\\s\\S]*", "$1");
            if (!samlLoginAddress.isEmpty() && !samlResponse.isEmpty()) {
                return new String[]{samlLoginAddress, samlResponse};
            }
        } else {
            if (samlLoginResponse.contains("name=\"sessionDataKey\"")) {
                throw new IllegalStateException("401: Authentication failed. Reason: Wrong credentials.");
            }
            if (samlLoginResponse.contains("Error 502")) {
                throw new QueryException("The request was not successful. Reason: response code: 502: response message: BAD GATEWAY");
            }
            if (samlLoginResponse.contains("503 Service Unavailable")) {
                throw new QueryException("The request was not successful. Reason: response code: 503: response message: Service Unavailable");
            }
        }
        throw new IllegalStateException("401: Authentication failed. Reason: Get SAML Data failed.");
    }

    private static String getShibbolethToken(String saml2LoginAddress, String samlResponse, Credentials credentials) throws IOException {
        final List<NameValuePair> samlLoginParameters = new ArrayList<>();
        samlLoginParameters.add(new BasicNameValuePair("SAMLResponse", samlResponse));
        try (CloseableHttpResponse shibbolethLoginResponse = NetUtils.openConnection(HttpMethod.POST, saml2LoginAddress, credentials, samlLoginParameters)) {
            if (shibbolethLoginResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                final String shibbolethCookieToken = extractShibbolethTokenFromCookies(saml2LoginAddress, credentials);
                if (!shibbolethCookieToken.isEmpty()) {
                    return shibbolethCookieToken;
                }
            }
            throw new IllegalStateException("401: Authentication failed. Reason: Get shibboleth cookie token failed.");
        }
    }

    private static String getSessionDataKey(String loginRequestResponseBody){
        if (loginRequestResponseBody.contains("Error 502")) {
            throw new QueryException("The request was not successful. Reason: response code: 502: response message: BAD GATEWAY");
        }
        if (loginRequestResponseBody.contains("name=\"sessionDataKey\"")) {
            return loginRequestResponseBody.replaceAll("[\\s\\S]*?name=\"sessionDataKey\" value='(.*?)'[\\s\\S]*", "$1");
        } else {
            return "";
        }
    }

    private static String extractShibbolethTokenFromCookies(String protectedURL, Credentials credentials) throws IOException {
        final List<Cookie> shibbolethCookies = NetUtils.getCookies(protectedURL, credentials);
        if (shibbolethCookies != null && !shibbolethCookies.isEmpty()) {
            final StringBuilder shibbolethCookieToken = new StringBuilder();
            for (Cookie shibbolethCookie : shibbolethCookies) {
                shibbolethCookieToken.append(shibbolethCookie.getName()).append("=").append(shibbolethCookie.getValue()).append(";");
            }
            return shibbolethCookieToken.toString();
        }
        return "";
    }

    public String getAuthenticationTokenName() {
        return TOKEN_NAME;
    }

    public String getAuthenticationTokenValue(String protectedURL) throws IOException {
        if (!this.cookieToken.isEmpty()) {
            return this.cookieToken;
        }
        int responseStatus;
        try (CloseableHttpResponse downloadRequestResponse = NetUtils.openConnection(HttpMethod.GET, protectedURL, this.credentials)) {
            responseStatus = downloadRequestResponse.getStatusLine().getStatusCode();
            if (responseStatus == HttpStatus.SC_FORBIDDEN) {
                throw new IllegalStateException("403: Forbidden. Reason: Account not authorized to download data from this collection.");
            }
            if (downloadRequestResponse.getFirstHeader("Content-Disposition") != null) {
                this.cookieToken = extractShibbolethTokenFromCookies(protectedURL, this.credentials);
                return this.cookieToken;
            }
            final String downloadRequestResponseBody = EntityUtils.toString(downloadRequestResponse.getEntity());
            final String sessionDataKey = getSessionDataKey(downloadRequestResponseBody);
            String[] samlData;
            if (sessionDataKey.isEmpty()) {
                samlData = getSAMLData(downloadRequestResponseBody);
            } else {
                samlData = getSAMLData(getSAMLLoginResponse(sessionDataKey, this.credentials));
            }
            this.cookieToken = getShibbolethToken(samlData[0], samlData[1], this.credentials);
            return this.cookieToken;
        }
    }
}
