package ro.cs.tao.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.user.User;

public class KeycloakTest {

    public static void main(String[] args) throws JsonProcessingException {
        final String url = "https://identity.cloudferro.com/auth";
        final String realm = "DUNIA";
        final String appClient = "tao";//"taoclient";
        final String secret = "Sg8YMYzpYuTr0TOk503CBw8Zc1un5hPJ";
        final String admin = "dunia.admin.api@cloudferro.com";
        final String pwd = "77X#8WhC%VHG$OoG#kt$8Zlf";
        final KeycloakClient client = new KeycloakClient(url, realm, appClient, secret, admin, pwd);
        User user;
        client.checkLoginCredentials("nicust@yahoo.com", "0change1me");
        /*final ObjectMapper mapper = new ObjectMapper();
        String token = "";
        user = client.checkToken(token);
        System.out.println(mapper.writeValueAsString(user));*/
        //final String token = user.getPreferences().get(0).getValue();
        //System.out.println(client.validate(token) ? "Token valid" : "Cannot validate token");
        System.exit(0);
    }
}
