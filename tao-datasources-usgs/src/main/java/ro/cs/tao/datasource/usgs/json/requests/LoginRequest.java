package ro.cs.tao.datasource.usgs.json.requests;

/**
 * Login request bean for USGS API 1.5
 */
public class LoginRequest extends BaseRequest{
    private String username;
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
