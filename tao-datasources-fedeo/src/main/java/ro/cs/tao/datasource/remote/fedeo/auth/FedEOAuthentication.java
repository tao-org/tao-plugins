package ro.cs.tao.datasource.remote.fedeo.auth;

import org.apache.http.auth.UsernamePasswordCredentials;
import ro.cs.tao.datasource.util.Utilities;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FedEOAuthentication {

    public static final String DOWNLOAD_URL_PROPERTY_NAME = "DOWNLOAD_URL";
    private static final String TOKEN_NAME = "Cookie";

    private static final Set<FedEOAuthenticationServiceProvider> serviceProviders = ServiceRegistryManager.getInstance().getServiceRegistry(FedEOAuthenticationServiceProvider.class).getServices();

    private final UsernamePasswordCredentials credentials;

    private final Map<String, FedEOAuthenticationServiceProvider> serviceProvidersMap = new HashMap<>();

    public FedEOAuthentication(UsernamePasswordCredentials credentials) {
        this.credentials = credentials;
    }

    public String getAuthenticationTokenName() {
        return TOKEN_NAME;
    }

    private FedEOAuthenticationServiceProvider getIntendedServiceProvider(String protectedDomain){
        for (FedEOAuthenticationServiceProvider serviceProvider : serviceProviders) {
            if (serviceProvider.intendedFor(protectedDomain)) {
                return serviceProvider;
            }
        }
        throw new IllegalStateException("401: Authentication failed. No FedEO authentication service provider found for: " + protectedDomain);
    }

    public String getAuthenticationTokenValue(String protectedURL) throws IOException {
        String protectedDomain = Utilities.getHostURL(protectedURL);
        FedEOAuthenticationServiceProvider serviceProvider = serviceProvidersMap.get(protectedDomain);
        if (serviceProvider == null) {
            serviceProvider = getIntendedServiceProvider(protectedDomain);
            this.serviceProvidersMap.put(protectedDomain, serviceProvider);
        }
        return serviceProvider.getAuthenticationTokenValue(protectedURL, this.credentials);
    }
}
