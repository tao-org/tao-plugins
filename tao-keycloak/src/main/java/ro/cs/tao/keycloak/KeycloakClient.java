package ro.cs.tao.keycloak;

import org.apache.commons.lang3.StringUtils;
import org.keycloak.OAuth2Constants;
import org.keycloak.RSATokenVerifier;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.security.TokenProvider;
import ro.cs.tao.serialization.JsonMapper;
import ro.cs.tao.user.User;
import ro.cs.tao.user.UserPreference;

import javax.ws.rs.core.Response;
import java.net.URL;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.logging.Logger;

public class KeycloakClient implements TokenProvider {
    private static final Logger logger = Logger.getLogger(KeycloakClient.class.getName());
    private static PublicKey realmKey;
    private static final Object lock = new Object();

    private final String realm;
    private final String authUrl;
    private final String clientId;
    private final String realmUrl;
    private final String clientSecret;
    private final String adminUser;
    private final String adminPwd;

    public KeycloakClient() {
        this(ConfigurationManager.getInstance().getValue(Keys.AUTH_SERVER_URL),
                ConfigurationManager.getInstance().getValue(Keys.REALM),
                ConfigurationManager.getInstance().getValue(Keys.RESOURCE),
                ConfigurationManager.getInstance().getValue(Keys.SECRET));
    }

    KeycloakClient(String authUrl, String realm, String clientId, String secret) {
        this.realm = realm;
        this.authUrl = authUrl;
        this.clientId = clientId;
        this.realmUrl = this.authUrl + "/realms/" + this.realm;
        this.clientSecret = secret;
        this.adminUser = ConfigurationManager.getInstance().getValue(Keys.ADMIN_USER);
        this.adminPwd = ConfigurationManager.getInstance().getValue(Keys.ADMIN_PWD);
    }

    KeycloakClient(String authUrl, String realm, String clientId, String secret, String admin, String admPwd) {
        this.realm = realm;
        this.authUrl = authUrl;
        this.clientId = clientId;
        this.realmUrl = this.authUrl + "/realms/" + this.realm;
        this.clientSecret = secret;
        this.adminUser = admin;
        this.adminPwd = admPwd;
    }

    @Override
    public String newToken(String user, String password) {
        return checkLoginCredentials(user, password).getPreferences().get(0).getValue();
    }

    public User checkLoginCredentials(final String username, final String password) {
        final AuthzClient authClient = getAuthClient();
        final AuthorizationRequest request = new AuthorizationRequest();
        request.addPermission("taoclient");
        final String token = authClient.obtainAccessToken(username, password).getToken();
        if (token == null) {
            throw new RuntimeException("Invalid credentials supplied");
        }
        final List<UserRepresentation> list = getAdminClient().realm(this.realm).users().search(username, true);
        final User user;
        if (list != null && list.size() == 1) {
            final UserRepresentation profile = list.get(0);
            user = new KeycloakUserAdapter().toTaoUser(profile);
            user.setPreferences(Collections.singletonList(new UserPreference("token", token)));
        } else {
            user = null;
        }
        return user;
    }

    public User createUser(final String username, final String password,
                           final String email, final String firstName, final String lastName) {
        final Keycloak client = getAdminClient();
        final UsersResource usersResource = client.realm(this.realm).users();
        final List<UserRepresentation> list = usersResource.search(username, true);
        if (list.size() == 1) {
            throw new IllegalArgumentException("Account already exists");
        } else {
            final UserRepresentation user = new UserRepresentation();
            user.setEnabled(true);
            user.setUsername(username);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmail(email);
            user.setAttributes(Collections.singletonMap("origin", Collections.singletonList("tao")));
            final Response response = usersResource.create(user);
            logger.fine(String.format("create user response: %s %s", response.getStatus(), response.getStatusInfo()));
            final String userId = CreatedResponseUtil.getCreatedId(response);
            logger.fine("new user id: " + userId);
            final CredentialRepresentation pwdCred = new CredentialRepresentation();
            pwdCred.setTemporary(false);
            pwdCred.setType(CredentialRepresentation.PASSWORD);
            pwdCred.setValue(password);
            final UserResource userResource = usersResource.get(userId);
            userResource.resetPassword(pwdCred);
        }
        return checkLoginCredentials(username, password);
    }

    public void changeUserProfile(final String username, final String password,
                                  final String newPassword, final String email,
                                  final String firstName, final String lastName) {
        final Keycloak client = getAdminClient();
        final UsersResource usersResource = client.realm(this.realm).users();
        final List<UserRepresentation> list = usersResource.search(username, true);
        if (list != null && list.size() == 1) {
            final UserRepresentation profile = list.get(0);
            profile.setEmail(email);
            profile.setFirstName(firstName);
            profile.setLastName(lastName);
            final UserResource userResource = usersResource.get(profile.getId());
            final List<String> actions = new ArrayList<>();
            if (StringUtils.isNotEmpty(newPassword) && !password.equals(newPassword)) {
                final CredentialRepresentation pwdCred = new CredentialRepresentation();
                pwdCred.setTemporary(false);
                pwdCred.setType(CredentialRepresentation.PASSWORD);
                pwdCred.setValue(newPassword);
                userResource.resetPassword(pwdCred);
                actions.add("UPDATE_PASSWORD");
            }
            userResource.update(profile);
            actions.add("UPDATE_PROFILE");
            userResource.executeActionsEmail(actions);
        }
    }

    public void removeUser(final String username) {
        final Keycloak client = getAdminClient();
        final UsersResource usersResource = client.realm(this.realm).users();
        final List<UserRepresentation> list = usersResource.search(username, true);
        if (list.size() == 1) {
            usersResource.get(list.get(0).getId()).remove();
        }
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

    private Keycloak getAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(this.authUrl)
                .realm(this.realm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(this.clientId)
                .clientSecret(this.clientSecret)
                .build();
    }

    private AuthzClient getAuthClient() {
        Configuration configuration = new Configuration(this.authUrl, this.realm, this.clientId,
                new HashMap<String, Object>() {{ put("secret", clientSecret); }}, null);
        return AuthzClient.create(configuration);
    }
}
