package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "access_token",
        "expires_in",
        "refresh_expires_in",
        "refresh_token",
        "token_type",
        "not-before-policy",
        "session_state"
})
public class Token {
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("expires_in")
    private long tokenExpiration;
    @JsonProperty("refresh_expires_in")
    private long refreshExpiration;
    @JsonProperty("refresh_token")
    private String refreshToken;
    @JsonProperty("token_type")
    private String tokenType;
    @JsonProperty("not-before-policy")
    private int notBeforePolicy;
    @JsonProperty("session_state")
    private String sessionState;

    @JsonProperty("access_token")
    public String getAccessToken() {
        return accessToken;
    }
    @JsonProperty("access_token")
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    @JsonProperty("expires_in")
    public long getTokenExpiration() {
        return tokenExpiration;
    }
    @JsonProperty("expires_in")
    public void setTokenExpiration(long tokenExpiration) {
        this.tokenExpiration = tokenExpiration;
    }
    @JsonProperty("refresh_expires_in")
    public long getRefreshExpiration() {
        return refreshExpiration;
    }
    @JsonProperty("refresh_expires_in")
    public void setRefreshExpiration(long refreshExpiration) {
        this.refreshExpiration = refreshExpiration;
    }
    @JsonProperty("refresh_token")
    public String getRefreshToken() {
        return refreshToken;
    }
    @JsonProperty("refresh_token")
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    @JsonProperty("token_type")
    public String getTokenType() {
        return tokenType;
    }
    @JsonProperty("token_type")
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
    @JsonProperty("not-before-policy")
    public int getNotBeforePolicy() {
        return notBeforePolicy;
    }
    @JsonProperty("not-before-policy")
    public void setNotBeforePolicy(int notBeforePolicy) {
        this.notBeforePolicy = notBeforePolicy;
    }
    @JsonProperty("session_state")
    public String getSessionState() {
        return sessionState;
    }
    @JsonProperty("session_state")
    public void setSessionState(String sessionState) {
        this.sessionState = sessionState;
    }
}
