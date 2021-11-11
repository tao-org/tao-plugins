package ro.cs.tao.keycloak;

import org.keycloak.RSATokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.idm.UserRepresentation;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.security.TokenProvider;
import ro.cs.tao.serialization.JsonMapper;
import ro.cs.tao.user.User;
import ro.cs.tao.user.UserPreference;

import java.net.URL;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class KeycloakClient implements TokenProvider {
    private static final Logger logger = Logger.getLogger(KeycloakClient.class.getName());
    private static PublicKey realmKey;
    private static final Object lock = new Object();

    private final String realm;
    private final String authUrl;
    private final String clientId;
    private final String realmUrl;

    public KeycloakClient() {
        this(ConfigurationManager.getInstance().getValue("keycloak.auth-server-url"),
             ConfigurationManager.getInstance().getValue("keycloak.realm"),
             ConfigurationManager.getInstance().getValue("keycloak.resource"));
    }

    KeycloakClient(String authUrl, String realm, String clientId) {
        this.realm = realm;
        this.authUrl = authUrl;
        this.clientId = clientId;
        this.realmUrl = this.authUrl + "/realms/" + this.realm;
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

    @Override
    public boolean validateToken(String token) {
        if (realmKey == null) {
            synchronized (lock) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> realmInfo = JsonMapper.instance().readValue(new URL(this.realmUrl).openStream(), Map.class);
                    realmKey = toPublicKey((String) realmInfo.get("public_key"));
                    if (realmKey == null) {
                        logger.severe("Cannot obtain public key from " + this.realmUrl);
                    }
                } catch (Exception e) {
                    logger.severe(e.getMessage());
                }
            }
        }
        if (realmKey != null && token != null) {
            RSATokenVerifier verifier = RSATokenVerifier.create(token);
            try {
                verifier.realmUrl(this.realmUrl)
                        .publicKey(realmKey)
                        .verify();
                return true;
            } catch (VerificationException e) {
                logger.severe("Verification of token " + token + " failed!");
                return false;
            }
        }
        return false;
    }

    private PublicKey toPublicKey(String publicKeyString) {
        try {
            byte[] publicBytes = Base64.getDecoder().decode(publicKeyString);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return null;
        }
    }
}
