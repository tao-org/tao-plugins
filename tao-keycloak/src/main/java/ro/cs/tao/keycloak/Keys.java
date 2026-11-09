package ro.cs.tao.keycloak;

/**
 * The Keys class contains a collection of constant string values
 * representing configuration keys related to Keycloak integration.
 * These constants are generally used to retrieve configuration settings
 * such as the authentication server URL, realm, resource, credentials,
 * and administrative account details.
 *
 * This class is intended to serve as a central location for these
 * configuration keys, which may be used throughout an application
 * to standardize access and prevent hardcoding.
 *
 * Constants defined in this class are:
 * - AUTH_SERVER_URL: Represents the configuration key for the Keycloak authentication server URL.
 * - REALM: Represents the configuration key for the Keycloak realm name.
 * - RESOURCE: Represents the configuration key for the Keycloak resource or client ID.
 * - SECRET: Represents the configuration key for the Keycloak client secret.
 * - ADMIN_USER: Represents the configuration key for the Keycloak administrative username.
 * - ADMIN_PWD: Represents the configuration key for the Keycloak administrative password.
 *
 * All the constants in this class are static and immutable.
 */
public class Keys {
    static final String AUTH_SERVER_URL = "keycloak.auth-server-url";
    static final String REALM = "keycloak.realm";
    static final String RESOURCE = "keycloak.resource";
    static final String SECRET = "keycloak.secret";
    static final String ADMIN_USER = "keycloak.admin.user";
    static final String ADMIN_PWD = "keycloak.admin.pwd";
}
