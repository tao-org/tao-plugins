package ro.cs.tao.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.user.User;

public class KeycloakTest {

    public static void main(String[] args) throws JsonProcessingException {
        final String url = "https://kc.brockmann-consult.de/auth";
        final String realm = "TAO";
        final String appClient = "taoclient";
        final String secret = "363614a1-ec24-4c11-ba2b-c086f5b12c90";
        final String admin = "taouser";
        //final String pwd = "taopassword";
        final String pwd = "1AK6otAuKqAuZZjG";
        final KeycloakClient client = new KeycloakClient(url, realm, appClient, secret, admin, pwd);
        User user;
        final ObjectMapper mapper = new ObjectMapper();
        /*try {
            user = client.checkLoginCredentials("taouser", "taopassword");
            System.out.println(user == null ? "Invalid credentials" : mapper.writeValueAsString(user));
        } catch (Exception e) {
        }*/
        /*user = client.checkLoginCredentials(admin, pwd);
        System.out.println(mapper.writeValueAsString(user));
        client.removeUser("avl1");*/
        /*user = client.createUser("avl1", "avl", "kraftek@gmail.com", "AVL", "User #1");
        System.out.println(mapper.writeValueAsString(user));*/
        user = client.checkLoginCredentials("avl1", "avl");
        System.out.println(mapper.writeValueAsString(user));
        final String token = user.getPreferences().get(0).getValue();
        System.out.println(client.validate(token) ? "Token valid" : "Cannot validate token");
        System.exit(0);
    }
}
