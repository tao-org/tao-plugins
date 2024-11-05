package ro.cs.tao.datasource.remote.fedeo.auth.providers;

import org.apache.http.HttpStatus;
import org.apache.http.auth.UsernamePasswordCredentials;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.fedeo.auth.FedEOAuthenticationServiceProvider;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.util.List;

public class InpeAuthenticationService extends FedEOAuthenticationServiceProvider {

    private static final List<String> protectedDomains = List.of(new String[]{
            "data.inpe.br"
    });


    @Override
    public List<String> getProtectedDomains() {
        return protectedDomains;
    }

    @Override
    protected String getAuthenticationTokenValue(String protectedURL, UsernamePasswordCredentials credentials) throws IOException {
        try (CloseableHttpResponse downloadRequestResponse = NetUtils.openConnection(HttpMethod.GET, protectedURL, null)) {
            if (downloadRequestResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new QueryException(String.format("The request was not successful. Reason: response code: %d: response message: %s", downloadRequestResponse.getStatusLine().getStatusCode(), downloadRequestResponse.getStatusLine().getReasonPhrase()));
            }
            if (downloadRequestResponse.getFirstHeader("Accept-Ranges") != null) {
                return "";
            }
            throw new IllegalStateException("Fail authentication for:" + protectedURL);
        }
    }
}
