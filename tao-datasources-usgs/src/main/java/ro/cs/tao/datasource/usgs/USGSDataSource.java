/*
 * Copyright (C) 2018 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package ro.cs.tao.datasource.usgs;

import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.datasource.usgs.json.handlers.LoginResponseHandler;
import ro.cs.tao.datasource.usgs.json.requests.LoginTokenRequest;
import ro.cs.tao.datasource.usgs.json.responses.LoginResponse;
import ro.cs.tao.datasource.usgs.parameters.USGSParameterProvider;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class USGSDataSource extends URLDataSource<USGSQuery, String> {
    private static final Properties props;
    private static String URL;
    private static String earthDataUsername;
    private static String earthDataPassword;

    static {
        props = new Properties();
        try {
            props.load(USGSDataSource.class.getResourceAsStream("usgs.properties"));
            URL = props.getProperty("usgs.base.url");
        } catch (IOException ignored) {
        }
    }

    public USGSDataSource() throws URISyntaxException {
        super(URL);
        setParameterProvider(new USGSParameterProvider(this));
        this.properties = USGSDataSource.props;
    }

    @Override
    public String defaultId() { return "USGS"; }

    @Override
    public int getMaximumAllowedTransfers() { return 2; }

    @Override
    public boolean requiresAuthentication() { return true; }

    @Override
    public String authenticate() {
        LoginTokenRequest request = new LoginTokenRequest();
        if (credentials == null) {
            throw new QueryException(String.format("Credentials not set for %s", getId()));
        }
        request.setUsername(credentials.getUserName());
        request.setToken(credentials.getPassword());
        String url = getConnectionString() + "login-token";
        //List<org.apache.http.NameValuePair> params = new ArrayList<>();
        //params.add(new BasicNameValuePair("jsonRequest", request.toString()));
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, url, (Credentials) null, request.toString())) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(response.getEntity());
                    if (body == null) {
                        throw new QueryException("Cannot retrieve API key [empty response body]");
                    }
                    JsonResponseParser<LoginResponse> parser = new JsonResponseParser<>(new LoginResponseHandler());
                    LoginResponse login = parser.parseValue(body);
                    if (login == null) {
                        throw new QueryException("Cannot retrieve API key [empty response body]");
                    }
                    String key = null;
                    if (login.getErrorCode() == null) {
                        key = login.getData();
                    } else {
                        throw new QueryException("Cannot retrieve API key [403:AUTH_INVALID]");
                    }
                    if (key == null) {
                        throw new QueryException(String.format("The API key could not be obtained [requestId:%s,apiVersion:%s,errorCode:%s,error:%s,data:%s",
                                login.getSessionId(), login.getVersion(), login.getErrorCode(), login.getErrorMessage(), login.getData()));
                    }
                    return key;
                case 401:
                    throw new QueryException("Cannot retrieve API key [401:not authorized]");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                            response.getStatusLine().getReasonPhrase()));
            }
        } catch (Exception ex) {
            throw new QueryException(ex);
        }
    }

    public void setEarthDataCredentials(String username, String password) {
        earthDataUsername = username;
        earthDataPassword = password;
    }

    public String getEarthDataToken() {
        return NetUtils.getAuthToken(earthDataUsername, earthDataPassword);
    }

    public UsernamePasswordCredentials getEarthDataCredentials() {
        return new UsernamePasswordCredentials(earthDataUsername, earthDataPassword);
    }

    @Override
    protected USGSQuery createQueryImpl(String code) {
        return new USGSQuery(this, code);
    }
}
