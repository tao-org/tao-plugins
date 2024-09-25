package ro.cs.tao.keycloak;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.keycloak.OAuth2Constants;
import org.keycloak.RSATokenVerifier;
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

import javax.ws.rs.core.Response;
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

public class KeycloakClient implements TokenProvider {
    public static final String USER_PLAN_ATTRIBUTE = "user_plan";
    private static final Logger logger = Logger.getLogger(KeycloakClient.class.getName());
    private static Map<String, PublicKey> realmKeys;
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

    public void setTokenKeeper(TokenKeeper tokenKeeper) {
        this.tokenKeeper = tokenKeeper;
    }

    public void setUserProvider(Supplier<UserProvider> provider) {
        this.userProvider = provider;
    }

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
            final ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
            try (Keycloak adminClient = getAdminClient()) {
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

    public User checkToken(final String token) {
        if (token == null) {
            throw new RuntimeException("Invalid token");
        }
        final RSATokenVerifier verifier = RSATokenVerifier.create(token);
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
                try (Keycloak adminClient = getAdminClient()) {
                    final List<UserRepresentation> list = adminClient.realm(this.realm).users().search(username, true);
                    if (list != null && list.size() == 1) {
                        final UserRepresentation profile = list.get(0);
                        user = new KeycloakUserAdapter().toTaoUser(profile);
                        //user.setPassword(password);
                        //user.setPreferences(Collections.singletonList(new UserPreference("token", token)));
                    }
                }
            }
        } catch (VerificationException e) {
            logger.finest(String.format("Verification of token %s failed [%s]",
                                        (token.length() <= 30 ? token : token.substring(0, 30)),
                                        e.getMessage()));
        }
        return user;
    }

    public User createUser(final String username, final String password,
                           final String email, final String firstName, final String lastName) {
        try (Keycloak client = getAdminClient()) {
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
    public boolean validate(String token) {
        if (token == null) return false;
        final RSATokenVerifier verifier = RSATokenVerifier.create(token);
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

    public String getUserPlanName(String username) {
        try (Keycloak adminClient = getAdminClient()) {
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

    private Keycloak getAdminClient() {
        return KeycloakBuilder.builder()
                              .serverUrl(this.authUrl)
                              .realm(this.realm)
                              .grantType(OAuth2Constants.PASSWORD)
                              .clientId("admin-cli")
                              .username(this.adminUser)
                              .password(this.adminPwd)
                              .build();
    }

    private AuthzClient getAuthClient() {
        Configuration configuration = new Configuration(this.authUrl, this.realm, this.clientId,
                                                        new HashMap<>() {{
                                                            put("secret", clientSecret);
                                                        }}, null);
        return AuthzClient.create(configuration);
    }

    private Token refreshToken(String refreshToken) {
        final Configuration configuration = new Configuration(this.authUrl, this.realm, this.clientId,
                                                              new HashMap<>() {{
                                                                  put("secret", clientSecret);
                                                              }}, null);
        final Http http = new Http(configuration, (params, headers) -> {});
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
