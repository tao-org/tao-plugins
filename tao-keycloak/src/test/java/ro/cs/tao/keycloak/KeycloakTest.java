package ro.cs.tao.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.user.User;

public class KeycloakTest {

    public static void main(String[] args) throws JsonProcessingException {
        final String url = "";
        final String realm = "TAO";
        final String appClient = "admin-cli";//"taoclient";
        final String secret = "";
        final String admin = "";
        final String pwd = "";
        final KeycloakClient client = new KeycloakClient(url, realm, appClient, secret, admin, pwd);
        User user;
        final ObjectMapper mapper = new ObjectMapper();
        String token = "";
        user = client.checkToken(token);
        System.out.println(mapper.writeValueAsString(user));
        //final String token = user.getPreferences().get(0).getValue();
        //System.out.println(client.validate(token) ? "Token valid" : "Cannot validate token");
        System.exit(0);
    }
}
