package ro.cs.tao.keycloak;

public class KeycloakTest {

    public static void main(String[] args) {
        final String url = "http://localhost:8180/auth";
        final String realm = "TAO";
        final String appClient = "tao-client";
        final KeycloakClient client = new KeycloakClient(url, realm, appClient);
        client.checkLoginCredentials("tao-user", "tao-password");
    }
}
