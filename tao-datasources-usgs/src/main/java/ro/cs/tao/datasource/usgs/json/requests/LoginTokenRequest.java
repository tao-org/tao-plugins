package ro.cs.tao.datasource.usgs.json.requests;

/**
 * Login request bean for USGS API 1.5
 */
public class LoginTokenRequest extends BaseRequest{
    private String username;
    private String token;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

}
