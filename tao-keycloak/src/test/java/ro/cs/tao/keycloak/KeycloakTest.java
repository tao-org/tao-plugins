package ro.cs.tao.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.user.User;

public class KeycloakTest {

    public static void main(String[] args) throws JsonProcessingException {
        final String url = "https://kc.brockmann-consult.de/auth";
        final String realm = "AVL";
        final String appClient = "admin-cli";//"taoclient";
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
        //user = client.checkLoginCredentials("kraftek", "cei7pitici.");
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJOQnAzSmp0QWw1WGZTVHhhOGZRY0NiSzlVSVQyOUFTOUx5NHVYdERxN1VjIn0.eyJleHAiOjE2NjU1NjkxNzIsImlhdCI6MTY2NTU2NTU3MiwianRpIjoiNzFlNjA3MjAtYzkyYi00YTk0LThlMGQtYmE4MmEwNmIxNzUyIiwiaXNzIjoiaHR0cHM6Ly9rYy5icm9ja21hbm4tY29uc3VsdC5kZS9hdXRoL3JlYWxtcy9BVkwiLCJhdWQiOlsicmVhbG0tbWFuYWdlbWVudCIsImF2bC14Yy1hcGkiLCJhY2NvdW50Il0sInN1YiI6IjhmN2ZiNjhmLTgzMmYtNDU5MS04OGIxLWM2ZDNlNDBjMjUzZiIsInR5cCI6IkJlYXJlciIsImF6cCI6InRhb2NsaWVudCIsInNlc3Npb25fc3RhdGUiOiJhNjU4MzYxNS1iZjNmLTQwMDktODA0YS1lY2Q0M2JmYjkwMzciLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbIioiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIm9mZmxpbmVfYWNjZXNzIiwidW1hX2F1dGhvcml6YXRpb24iLCJ0YW8tYWRtaW4iLCJkZWZhdWx0LXJvbGVzLXRhbyJdfSwicmVzb3VyY2VfYWNjZXNzIjp7InJlYWxtLW1hbmFnZW1lbnQiOnsicm9sZXMiOlsidmlldy1pZGVudGl0eS1wcm92aWRlcnMiLCJtYW5hZ2UtaWRlbnRpdHktcHJvdmlkZXJzIiwiaW1wZXJzb25hdGlvbiIsImNyZWF0ZS1jbGllbnQiLCJtYW5hZ2UtdXNlcnMiLCJ2aWV3LWF1dGhvcml6YXRpb24iLCJxdWVyeS1jbGllbnRzIiwicXVlcnktdXNlcnMiLCJtYW5hZ2UtZXZlbnRzIiwidmlldy1ldmVudHMiLCJ2aWV3LXVzZXJzIiwidmlldy1jbGllbnRzIiwibWFuYWdlLWF1dGhvcml6YXRpb24iLCJtYW5hZ2UtY2xpZW50cyIsInF1ZXJ5LWdyb3VwcyJdfSwiYXZsLXhjLWFwaSI6eyJyb2xlcyI6WyJyZWFkOmRhdGFzZXQ6YXZsLXNjcmF0Y2h-KiIsInJlYWQ6ZGF0YXNldDphdmwtc3RhZ2luZ34qIiwic3lzdGVtX3VzZXIiLCJyZWFkOmRhdGFzZXQ6YXZsLXVzZXItcHVibGljfioiLCJyZWFkOmRhdGFzZXQ6YXZsLWRhdGF-KiIsInJlYWQ6ZGF0YXNldDphdmwtdXNlcn4kdXNlcm5hbWUvKiIsInJlYWQ6ZGF0YXNldDphdmwtZGVtb34qIiwicmVhZDpkYXRhc2V0OmF2bC10ZXN0fioiXX0sImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoiZW1haWwgcHJvZmlsZSIsInNpZCI6ImE2NTgzNjE1LWJmM2YtNDAwOS04MDRhLWVjZDQzYmZiOTAzNyIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJuYW1lIjoiQ29zbWluIENhcmEiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJrcmFmdGVrIiwiZ2l2ZW5fbmFtZSI6IkNvc21pbiIsImZhbWlseV9uYW1lIjoiQ2FyYSIsImVtYWlsIjoia3JhZnRla0BnbWFpbC5jb20ifQ.QCvncqRIgcQxzcTDFiLHXzGU38gEA04fEmP-aukjQbg18DyB_urzWd4X4DSH8NHK1RQY8TBrR4-gL3vuTNwnVEXtfPPsfjyY6xC344D-bv2m92kNJuX-WJ-7GEh8t-MqnNLnIxlMr6LAIaqrF0OUnYD2PsQNNz3dzVtpgnhIetjDYh1XDj7QbOvHh-MdkUBRkDSQyEwNfmHLuohC8SGpVuaXLcdnM1atC1SGkmoB1rVVtu8csZpfM2env0N5ELaVh4KgKudYUftO5jNCMn1kVGWZvUakBOhD2zYDRFCjGUlb90p_39eFswdQwoeW64KWkwFnbXebiwS0yj5nFiNbAg";
        user = client.checkToken(token);
        System.out.println(mapper.writeValueAsString(user));
        //final String token = user.getPreferences().get(0).getValue();
        //System.out.println(client.validate(token) ? "Token valid" : "Cannot validate token");
        System.exit(0);
    }
}
