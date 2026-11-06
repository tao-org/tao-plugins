package ro.cs.tao.keycloak;

import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.keycloak.OAuth2Constants;
import org.keycloak.TokenVerifier;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.util.Http;
import org.keycloak.common.VerificationException;
import org.keycloak.jose.jws.JWSHeader;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.persistence.UserProvider;
import ro.cs.tao.security.Token;
import ro.cs.tao.security.TokenKeeper;
import ro.cs.tao.security.TokenProvider;
import ro.cs.tao.user.User;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * The KeycloakClient class provides an interface for interacting with a Keycloak server
 * to manage authentication, user management, and token-related operations.
 * It implements the TokenProvider interface and extends Object.
 */
public class KeycloakClient implements TokenProvider {
    public static final String USER_PLAN_ATTRIBUTE = "user_plan";
    private static final Logger logger = Logger.getLogger(KeycloakClient.class.getName());
    private static Map<String, PublicKey> realmKeys;
    private static Keycloak adminClient;
    private static final Object lock = new Object();

    private final String realm;
    private final String authUrl;
    private final String clientId;
    private final String realmUrl;
    private final String clientSecret;
    private final String adminUser;
    private final String adminPwd;
    private TokenKeeper tokenKeeper;
    private Supplier<UserProvider> userProvider;

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

    public KeycloakClient(String authUrl, String realm, String clientId, String secret, String admin, String admPwd) {
        this.realm = realm;
        this.authUrl = authUrl;
        this.clientId = clientId;
        this.realmUrl = this.authUrl + "/realms/" + this.realm;
        this.clientSecret = secret;
        this.adminUser = admin;
        this.adminPwd = admPwd;
    }

    /**
     * Sets the TokenKeeper for managing token-related operations.
     *
     * @param tokenKeeper the TokenKeeper instance to be used for token management
     */
    public void setTokenKeeper(TokenKeeper tokenKeeper) {
        this.tokenKeeper = tokenKeeper;
    }

    /**
     * Sets the UserProvider to be used by the KeycloakClient.
     * The UserProvider is supplied using a functional interface, allowing dynamic retrieval.
     *
     * @param provider a supplier that provides an instance of UserProvider.
     *                 This UserProvider will be used for user-related operations by the KeycloakClient.
     */
    public void setUserProvider(Supplier<UserProvider> provider) {
        this.userProvider = provider;
    }

    /**
     * Retrieves the username of the administrator user for the Keycloak client.
     *
     * @return the username of the admin user as a string
     */
    public String getAdminUser() {
        return adminUser;
    }

    @Override
    public Token newToken(String user, String password) {
        Token token = null;
        if (this.tokenKeeper != null) {
            token = this.tokenKeeper.getToken(user);
        }
        if (token == null) {
            final AuthzClient authClient = getAuthClient();
            final AuthorizationRequest request = new AuthorizationRequest();
            request.addPermission(this.clientId);

            final AccessTokenResponse response = authClient.obtainAccessToken(user, password);
            token = new Token(response.getToken(), response.getRefreshToken(), (int) response.getExpiresIn());
        }
        return token;
    }

    @Override
    public Token newTokenFromCode(String code) {
        Header header = new BasicHeader("Content-Type", "application/x-www-form-urlencoded");
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "authorization_code"));
        params.add(new BasicNameValuePair("client_id", this.clientId));
        params.add(new BasicNameValuePair("client_secret", this.clientSecret));
        params.add(new BasicNameValuePair("code", code));
        params.add(new BasicNameValuePair("redirect_uri", ConfigurationManager.getInstance().getValue("tao.ui.base") + "/login.html"));
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, this.realmUrl + "/protocol/openid-connect/token", header, params)) {
            final ObjectMapper mapper = new ObjectMapper().rebuild()
                                                           .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                                           .build();
            final AccessTokenResponse tokenResponse = mapper.readerFor(AccessTokenResponse.class)
                                                            .readValue(EntityUtils.toString(response.getEntity()));
            if (tokenResponse.getError() != null) {
                throw new RuntimeException(tokenResponse.getErrorDescription());
            }
            return new Token(tokenResponse.getToken(), tokenResponse.getRefreshToken(), tokenResponse.getIdToken (), (int) tokenResponse.getExpiresIn());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Token newToken(String refreshToken) {
        return refreshToken(refreshToken);
    }

    @Override
    public boolean logout(String userId) {
        try {
            final Keycloak adminClient = getAdminClient();
            adminClient.realm(this.realm).users().get(userId).logout();
        } catch (Throwable t) {
            logger.warning("Error when logging out of Keycloak: " + t.getMessage());
        }
        return true;
    }

    /**
     * Validates the given username and password credentials by interacting with a Keycloak server
     * and retrieves the corresponding user details if authentication is successful.
     *
     * @param username the username of the user attempting to log in
     * @param password the password associated with the specified username
     * @return a {@code User} object representing the authenticated user, or {@code null} if the
     *         authentication fails or the user cannot be retrieved
     * @throws RuntimeException if the provided credentials are invalid or any other error occurs
     */
    public User checkLoginCredentials(final String username, final String password) {
        final AuthzClient authClient = getAuthClient();
        final AuthorizationRequest request = new AuthorizationRequest();
        request.addPermission(this.clientId);
        final AccessTokenResponse tokenResponse = authClient.obtainAccessToken(username, password);
        final String token = tokenResponse.getToken();
        if (token == null) {
            throw new RuntimeException("Invalid credentials supplied");
        }
        if (this.tokenKeeper != null) {
            this.tokenKeeper.put(new Token(tokenResponse.getToken(),
                                           tokenResponse.getRefreshToken(),
                                           tokenResponse.getIdToken(),
                                           (int) tokenResponse.getExpiresIn()),
                                 username);
        }
        User user = null;
        if (this.userProvider != null) {
            user = this.userProvider.get().getByName(username);
        }
        if (user == null) {
            try {//(Keycloak adminClient = getAdminClient()) {
                final Keycloak adminClient = getAdminClient();
                final List<UserRepresentation> list = adminClient.realm(this.realm).users().search(username, true);
                if (list != null && list.size() == 1) {
                    final UserRepresentation profile = list.get(0);
                    user = new KeycloakUserAdapter().toTaoUser(profile);
                    user.setPassword(password);
                    //user.setPreferences(Collections.singletonList(new UserPreference("token", token)));
                } else {
                    user = null;
                }
            } catch (Throwable t) {
                logger.severe(t.getMessage());
                throw t;
            }
        } else {
            user.setPassword(password);
        }
        return user;
    }

    /**
     * Verifies the provided authorization code by exchanging it for a token and uses
     * the retrieved token to validate and fetch the associated user's details.
     *
     * @param code the authorization code used to authenticate and retrieve the user's details
     * @return a {@code User} object representing the authenticated user
     * @throws RuntimeException if the supplied authorization code is invalid or authentication fails
     */
    public User checkLoginCredentials(final String code) {
        Token token = newTokenFromCode(code);
        if (token == null) {
            throw new RuntimeException("Invalid credentials supplied");
        }
        final User user = checkToken(token.getToken());
        if (this.tokenKeeper != null) {
            this.tokenKeeper.put(token, user.getId());
        }
        return user;
    }

    /**
     * Validates the given token and retrieves the associated user details.
     * This method performs token verification and checks if the token
     * corresponds to a valid user. If successful, it provides the {@code User}
     * object associated with the token.
     *
     * @param token the string representation of the token to be verified; must not be null
     * @return a {@code User} object representing the user associated with the valid token,
     *         or {@code null} if the token is invalid or the user cannot be retrieved
     * @throws RuntimeException if the provided token is null or any error occurs during verification
     */
    public User checkToken(final String token) {
        if (token == null) {
            throw new RuntimeException("Invalid token");
        }
        final TokenVerifier<AccessToken> verifier = TokenVerifier.create(token, AccessToken.class);
        if (realmKeys == null || realmKeys.isEmpty()) {
            synchronized (lock) {
                realmKeys = new HashMap<>();
                try {
                    retrievePublicKeyFromCertsEndpoint(verifier.getHeader());
                } catch (Exception e) {
                    logger.severe(e.getMessage());
                }
            }
        }
        User user = null;
        try {
            verifier.realmUrl(this.realmUrl)
                    .publicKey(realmKeys.get(verifier.getHeader().getKeyId()))
                    .verify();
            AccessToken accessToken = verifier.getToken();
            String username = accessToken.getPreferredUsername();
            if (this.tokenKeeper != null) {
                this.tokenKeeper.put(new Token(token, null, (int) Duration.between(Instant.ofEpochSecond(accessToken.getIat()),
                                                                                      Instant.ofEpochSecond(accessToken.getExp())).getSeconds()),
                                     username);
            }
            if (this.userProvider != null) {
                user = this.userProvider.get().getByName(username);
            }
            if (user == null) {
                //try (Keycloak adminClient = getAdminClient()) {
                final Keycloak adminClient = getAdminClient();
                final List<UserRepresentation> list = adminClient.realm(this.realm).users().search(username, true);
                if (list != null && list.size() == 1) {
                    final UserRepresentation profile = list.get(0);
                    user = new KeycloakUserAdapter().toTaoUser(profile);
                    //user.setPassword(password);
                    //user.setPreferences(Collections.singletonList(new UserPreference("token", token)));
                }
                //}
            }
        } catch (VerificationException e) {
            logger.finest(String.format("Verification of token %s failed [%s]",
                                        (token.length() <= 30 ? token : token.substring(0, 30)),
                                        e.getMessage()));
        }
        return user;
    }

    /**
     * Creates a new user in the Keycloak system with the specified credentials and attributes.
     * If a user with the provided username already exists, an exception is thrown.
     *
     * @param username the username for the new user to be created
     * @param password the password for the new user to be created
     * @param email the email address to associate with the new user
     * @param firstName the first name of the new user
     * @param lastName the last name of the new user
     * @return a {@code User} object representing the newly created user, if the operation is successful
     * @throws IllegalArgumentException if a user with the specified username already exists
     */
    public User createUser(final String username, final String password,
                           final String email, final String firstName, final String lastName) {
        //try (Keycloak client = getAdminClient()) {
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
        //}
    }

    /**
     * Updates the profile of an existing user in the Keycloak system.
     * This includes updating email, first name, last name, and optionally resetting the user's password.
     * If the password is updated, the user will be required to update their password on the next login.
     *
     * @param username     the username of the user whose profile is to be updated
     * @param password     the current password of the user
     * @param newPassword  the new password to set for the user, if provided and different from the current password
     * @param email        the updated email address to associate with the user
     * @param firstName    the updated first name of the user
     * @param lastName     the updated last name of the user
     */
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

    /**
     * Removes a user from the Keycloak system using their username.
     * If there is exactly one user matching the provided username, that user will be removed.
     *
     * @param username the username of the user to be removed
     */
    public void removeUser(final String username) {
        final Keycloak client = getAdminClient();
        final UsersResource usersResource = client.realm(this.realm).users();
        final List<UserRepresentation> list = usersResource.search(username, true);
        if (list.size() == 1) {
            usersResource.get(list.get(0).getId()).remove();
        }
    }

    @Override
    public boolean validate(String token) {
        if (token == null) return false;
        final TokenVerifier<AccessToken> verifier = TokenVerifier.create(token, AccessToken.class);
        if (realmKeys == null || realmKeys.isEmpty()) {
            synchronized (lock) {
                realmKeys = new HashMap<>();
                try {
                    retrievePublicKeyFromCertsEndpoint(verifier.getHeader());
                } catch (Exception e) {
                    logger.severe(e.getMessage());
                }
            }
        }
        try {
            verifier.realmUrl(this.realmUrl)
                    .publicKey(realmKeys.get(verifier.getHeader().getKeyId()))
                    .verify();
            AccessToken accessToken = verifier.getToken();
            //String username = accessToken.getPreferredUsername();
            String userId = accessToken.getSubject();
            if (this.tokenKeeper != null && this.tokenKeeper.getFullToken(token) == null) {
                this.tokenKeeper.put(new Token(token, null, (int) Duration.between(Instant.ofEpochSecond(accessToken.getIat()),
                                                                                   Instant.ofEpochSecond(accessToken.getExp())).getSeconds()),
                                     userId);
            }
            return true;
        } catch (VerificationException e) {
            logger.finest(String.format("Verification of token %s failed [%s]",
                                        (token.length() <= 30 ? token : token.substring(0, 30)),
                                        e.getMessage()));
        }
        return false;
    }

    /**
     * Retrieves the first associated plan name for a specified user from their attributes in Keycloak.
     * Searches for the user using their unique username and extracts the plan name
     * if available in the user's attributes.
     *
     * @param username the username of the user whose plan name is to be retrieved
     * @return the name of the user's plan if it exists; otherwise, {@code null}
     * @throws Throwable if any exception occurs during the retrieval process
     */
    public String getUserPlanName(String username) {
        try { //(Keycloak adminClient = getAdminClient()) {
            Keycloak adminClient = getAdminClient();
            final List<UserRepresentation> list = adminClient.realm(this.realm).users().search(username, true);
            if (list != null && list.size() == 1) {
                final UserRepresentation profile = list.get(0);
                final Map<String, List<String>> attributes = profile.getAttributes();
                List<String> plans = attributes != null
                                     ? attributes.getOrDefault(KeycloakClient.USER_PLAN_ATTRIBUTE, new ArrayList<>())
                                     : new ArrayList<>();
                return plans.isEmpty() ? null : plans.get(0);
            } else {
                return null;
            }
        } catch (Throwable t) {
            logger.severe(t.getMessage());
            throw t;
        }
    }

    /**
     * Converts a map containing key information into an RSA public key.
     * The method extracts the modulus and exponent from the provided map,
     * decodes them, and generates a {@code PublicKey} using the RSA algorithm.
     *
     * @param keyInfo a map containing the key information. It must include
     *                the "n" key for the base64-encoded modulus and the "e" key
     *                for the base64-encoded public exponent.
     * @return the generated {@code PublicKey} object if the conversion is successful;
     *         {@code null} if the conversion fails due to invalid input or errors during processing.
     */
    private PublicKey toPublicKey(Map<String, Object> keyInfo) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            String modulusBase64 = (String) keyInfo.get("n");
            String exponentBase64 = (String) keyInfo.get("e");

            // see org.keycloak.jose.jwk.JWKBuilder#rs256
            Base64.Decoder urlDecoder = Base64.getUrlDecoder();
            BigInteger modulus = new BigInteger(1, urlDecoder.decode(modulusBase64));
            BigInteger publicExponent = new BigInteger(1, urlDecoder.decode(exponentBase64));

            return keyFactory.generatePublic(new RSAPublicKeySpec(modulus, publicExponent));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            return null;
        }
    }

    /**
     * Retrieves public keys from the certificates endpoint of the configured Keycloak realm
     * and updates the internal mapping of key IDs to public keys.
     *
     * @param jwsHeader the JSON Web Signature (JWS) header containing details such as the key ID (kid)
     *                  used to match and retrieve the corresponding public key
     */
    private void retrievePublicKeyFromCertsEndpoint(JWSHeader jwsHeader) {
        try {
            ObjectMapper om = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> certInfos = om.readValue(new URL(this.realmUrl + "/protocol/openid-connect/certs").openStream(), Map.class);

            List<Map<String, Object>> keys = (List<Map<String, Object>>) certInfos.get("keys");

            for (Map<String, Object> key : keys) {
                realmKeys.put((String) key.get("kid"), toPublicKey(key));
            }
        } catch (Exception e) {
            logger.warning(ExceptionUtils.getStackTrace(logger, e));
        }
    }

    /**
     * Retrieves the administrative client for interacting with the Keycloak server. If the admin client
     * instance is not initialized or is closed, a new instance is constructed using the configured server URL,
     * realm, and administrator credentials. If the existing client is still valid, its token is refreshed or renewed
     * to ensure continued access.
     *
     * @return an instance of {@code Keycloak} representing the administrative client.
     */
    private Keycloak getAdminClient() {
        if (adminClient == null || adminClient.isClosed()) {
            adminClient = KeycloakBuilder.builder()
                                         .serverUrl(this.authUrl)
                                         .realm(this.realm)
                                         .grantType(OAuth2Constants.PASSWORD)
                                         .clientId("admin-cli")
                                         .username(this.adminUser)
                                         .password(this.adminPwd)
                                         .build();
        } else {
            // Either returns the current valid token, or refreshes it if expired, or obtains a new one
            adminClient.tokenManager().getAccessToken();
        }
        return adminClient;
    }

    /**
     * Initializes and retrieves an instance of {@code AuthzClient} for interacting with the Keycloak server.
     * This client is configured using the authentication URL, realm, client ID, and client secret.
     *
     * @return an instance of {@code AuthzClient} configured for the Keycloak server.
     */
    private AuthzClient getAuthClient() {
        Configuration configuration = new Configuration(this.authUrl, this.realm, this.clientId,
                                                        new HashMap<>() {{
                                                            put("secret", clientSecret);
                                                        }}, null);
        return AuthzClient.create(configuration);
    }

    /**
     * Refreshes a currently expired or soon-to-expire token using the provided refresh token.
     * This method interacts with the Keycloak server to obtain a new access token and refresh token.
     *
     * @param refreshToken the string representation of the refresh token to be used for token renewal
     * @return a {@code Token} object containing the new access token, refresh token, and expiration time
     * @throws RuntimeException if the refresh process fails or the provided refresh token is invalid
     */
    private Token refreshToken(String refreshToken) {
        final Configuration configuration = new Configuration(this.authUrl, this.realm, this.clientId,
                                                              new HashMap<>() {{
                                                                  put("secret", clientSecret);
                                                              }}, null);
        final Http http = new Http(configuration, configuration.getClientCredentialsProvider());
        final String url = this.realmUrl + "/protocol/openid-connect/token";
        final AccessTokenResponse response = http.<AccessTokenResponse>post(url)
                .authentication()
                .client()
                .form()
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken)
                .param("client_id", clientId)
                .param("client_secret", clientSecret)
                .response()
                .json(AccessTokenResponse.class)
                .execute();
        return new Token(response.getToken(), response.getRefreshToken(), (int) response.getExpiresIn());
    }
}
