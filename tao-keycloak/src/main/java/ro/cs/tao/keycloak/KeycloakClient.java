package ro.cs.tao.keycloak;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.security.TokenProvider;
import ro.cs.tao.user.User;
import ro.cs.tao.user.UserPreference;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class KeycloakClient implements TokenProvider {
    private static final Logger logger = Logger.getLogger(KeycloakClient.class.getName());

    private final String realm;
    private final String authUrl;
    private final String clientId;

    public KeycloakClient() {
        final ConfigurationProvider configurationProvider = ConfigurationManager.getInstance();
        realm = configurationProvider.getValue("keycloak.realm");
        authUrl = configurationProvider.getValue("keycloak.auth-server-url");
        clientId = configurationProvider.getValue("keycloak.resource");
    }

    KeycloakClient(String authUrl, String realm, String clientId) {
        this.realm = realm;
        this.authUrl = authUrl;
        this.clientId = clientId;
    }

    @Override
    public String newToken(String user, String password) {
        return checkLoginCredentials(user, password).getPreferences().get(0).getValue();
    }

    public User checkLoginCredentials(final String username, final String password) {
        final Keycloak client = Keycloak.getInstance(this.authUrl, this.realm, username, password, this.clientId);
        final List<UserRepresentation> list = client.realm(this.realm).users().search(username, true);
        final User user;
        if (list != null && list.size() == 1) {
            final UserRepresentation profile = list.get(0);
            user = new User();
            user.setUsername(username);
            user.setExternal(true);
            user.setFirstName(profile.getFirstName());
            user.setLastName(profile.getLastName());
            user.setEmail(profile.getEmail());
            user.setOrganization("n/a");
            user.setCreated(LocalDateTime.now());
            user.setPreferences(new ArrayList<UserPreference>() {{
                new UserPreference("token", client.tokenManager().getAccessTokenString());
            }});
        } else {
            user = null;
        }
        return user;
    }
}
