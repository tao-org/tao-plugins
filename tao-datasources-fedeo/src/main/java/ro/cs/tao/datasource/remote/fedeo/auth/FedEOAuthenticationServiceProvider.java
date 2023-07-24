package ro.cs.tao.datasource.remote.fedeo.auth;

import org.apache.http.auth.UsernamePasswordCredentials;

import java.io.IOException;
import java.util.List;

public abstract class FedEOAuthenticationServiceProvider {

    public final boolean intendedFor(String protectedDomain){
        return getProtectedDomains().contains(protectedDomain);
    }

    protected abstract List<String> getProtectedDomains();

    protected abstract String getAuthenticationTokenValue(String protectedURL, UsernamePasswordCredentials credentials) throws IOException;

}
