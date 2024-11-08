package ro.cs.tao.datasource.remote.das;/*
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

import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.das.common.Token;
import ro.cs.tao.datasource.remote.das.json.LoginResponseHandler;
import ro.cs.tao.datasource.remote.das.parameters.DASParameterProvider;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class DASDataSource extends URLDataSource<DASQuery, Token> {
    private static final Properties props;
    private static String LOGIN_URL;
    private static String SEARCH_URL;
    private static String DOWNLOAD_URL;

    static {
        props = new Properties();
        try {
            props.load(DASDataSource.class.getResourceAsStream("das.properties"));
            SEARCH_URL = props.getProperty("search.url");
            LOGIN_URL = props.getProperty("login.url");
            DOWNLOAD_URL = props.getProperty("download.url");
        } catch (IOException ignored) {
        }
    }

    public DASDataSource() throws URISyntaxException {
        super(SEARCH_URL);
        setParameterProvider(new DASParameterProvider(this));
        this.properties = DASDataSource.props;
    }

    @Override
    public String defaultId() { return "Copernicus DataSpace"; }

    @Override
    public int getMaximumAllowedTransfers() { return 2; }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(SEARCH_URL, null, null);
    }

    @Override
    public boolean requiresAuthentication() { return true; }

    @Override
    public void setCredentials(String username, String password) {
        super.setCredentials(username, password);
    }

    @Override
    public String getConnectionString(String sensorName) {
        return /*sensorName.equals("Sentinel1-orbit-files") ? DOWNLOAD_URL : */super.getConnectionString(sensorName);
    }

    @Override
    public Token authenticate() {
        if (credentials == null) {
            throw new QueryException(String.format("Credentials not set for %s", getId()));
        }
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", "cdse-public"));
        params.add(new BasicNameValuePair("username", credentials.getUserName()));
        params.add(new BasicNameValuePair("password", credentials.getPassword()));
        params.add(new BasicNameValuePair("grant_type", "password"));
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, LOGIN_URL, (Credentials) null, params)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(response.getEntity());
                    if (body == null) {
                        throw new QueryException("Cannot retrieve API key [empty response body]");
                    }
                    ResponseParser<Token> parser = new JsonResponseParser<>(new LoginResponseHandler());
                    final Token token = parser.parseValue(body);
                    if (token != null) {
                        token.setCreated(LocalDateTime.now());
                        return token;
                    } else {
                        throw new QueryException(String.format("Cannot retrieve API key [received: %s]", body));
                    }
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

    @Override
    public Token reauthenticate(String refreshToken) {
        if (refreshToken == null) {
            throw new QueryException(String.format("Refresh token not set for %s", getId()));
        }
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", "cdse-public"));
        params.add(new BasicNameValuePair("grant_type", "refresh_token"));
        params.add(new BasicNameValuePair("refresh_token", refreshToken));
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, LOGIN_URL, (Credentials) null, params)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(response.getEntity());
                    if (body == null) {
                        throw new QueryException("Cannot retrieve API key [empty response body]");
                    }
                    ResponseParser<Token> parser = new JsonResponseParser<>(new LoginResponseHandler());
                    final Token token = parser.parseValue(body);
                    if (token != null) {
                        token.setCreated(LocalDateTime.now());
                        return token;
                    } else {
                        throw new QueryException(String.format("Cannot retrieve API key [received: %s]", body));
                    }
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

    @Override
    protected DASQuery createQueryImpl(String sensorName) {
        return new DASQuery(this, sensorName);
    }
}
