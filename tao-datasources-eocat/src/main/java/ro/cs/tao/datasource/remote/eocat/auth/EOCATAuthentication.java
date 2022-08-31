package ro.cs.tao.datasource.remote.eocat.auth;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
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

    private String[] getSAMLData(String sessionDataKey) throws IOException {
        List<NameValuePair> samlLoginParameters = new ArrayList<>();
        samlLoginParameters.add(new BasicNameValuePair("tocommonauth", "true"));
        samlLoginParameters.add(new BasicNameValuePair("username", this.credentials.getUserName()));
        samlLoginParameters.add(new BasicNameValuePair("password", this.credentials.getPassword()));
        samlLoginParameters.add(new BasicNameValuePair("sessionDataKey", sessionDataKey));
        try (CloseableHttpResponse samlLoginResponse = NetUtils.openConnection(HttpMethod.POST, SAML_LOGIN_ADDRESS, (Credentials) null, samlLoginParameters)) {
            String samlLoginRequestResponseBody = EntityUtils.toString(samlLoginResponse.getEntity());
            String saml2LoginAddress = samlLoginRequestResponseBody.replaceAll("[\\s\\S]*?method='post' action='(.*?)'[\\s\\S]*", "$1");
            String samlResponse = samlLoginRequestResponseBody.replaceAll("[\\s\\S]*?name='SAMLResponse' value='(.*?)'[\\s\\S]*", "$1");
            if (samlResponse.isEmpty()) {
                throw new IllegalStateException("Get SAMLResponse failed.");
            }
            return new String[]{saml2LoginAddress, samlResponse};
        }
    }

    private String getShibbolethToken(String saml2LoginAddress, String samlResponse) throws IOException {
        List<NameValuePair> samlLoginParameters = new ArrayList<>();
        samlLoginParameters.add(new BasicNameValuePair("SAMLResponse", samlResponse));
        try (CloseableHttpResponse shibbolethLoginResponse = NetUtils.openConnection(HttpMethod.POST, saml2LoginAddress, (Credentials) null, samlLoginParameters)) {
            Header shibbolethCookieToken = shibbolethLoginResponse.getFirstHeader("Set-Cookie");
            if (shibbolethCookieToken != null && !shibbolethCookieToken.getValue().isEmpty()) {
                return shibbolethCookieToken.getValue();
            }
            String errMsg = "Get shibboleth cookie token failed. Reason: ";
            if (shibbolethLoginResponse.getStatusLine().getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY) {
                errMsg = errMsg + "Status: " + shibbolethLoginResponse.getStatusLine().getStatusCode();
            } else {
                errMsg = errMsg + shibbolethLoginResponse.getFirstHeader("Location").getValue();
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
        try (CloseableHttpResponse downloadRequestResponse = NetUtils.openConnection(HttpMethod.GET, protectedURL, getAuthenticationTokenName(), currentCookieToken, null)) {
            responseStatus = downloadRequestResponse.getStatusLine().getStatusCode();
            if (responseStatus != HttpStatus.SC_OK) {
                throw new QueryException(String.format("The request was not successful. Reason: response code: %d: response message: %s", downloadRequestResponse.getStatusLine().getStatusCode(), downloadRequestResponse.getStatusLine().getReasonPhrase()));
            }
            if (downloadRequestResponse.getFirstHeader("Content-Disposition") != null) {
                return currentCookieToken;
            }
            String downloadRequestResponseBody = EntityUtils.toString(downloadRequestResponse.getEntity());
            String sessionDataKey = downloadRequestResponseBody.replaceAll("[\\s\\S]*?name=\"sessionDataKey\" value='(.*?)'[\\s\\S]*", "$1");
            String[] samlData = getSAMLData(sessionDataKey);
            this.cookieToken = getShibbolethToken(samlData[0], samlData[1]);
            return this.cookieToken;
        }
    }
}
