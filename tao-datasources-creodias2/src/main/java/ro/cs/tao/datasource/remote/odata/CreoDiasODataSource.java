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
package ro.cs.tao.datasource.remote.odata;

import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.odata.common.Token;
import ro.cs.tao.datasource.remote.odata.json.LoginResponseHandler;
import ro.cs.tao.datasource.remote.odata.parameters.CreoDias2ParameterProvider;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class CreoDiasODataSource extends URLDataSource<CreoDiasODataQuery, Token> {
    private static final Properties props;
    private static String LOGIN_URL;
    private static String SEARCH_URL;

    static {
        props = new Properties();
        try {
            props.load(CreoDiasODataSource.class.getResourceAsStream("creodias2.properties"));
            SEARCH_URL = props.getProperty("search.url");
            LOGIN_URL = props.getProperty("login.url");
        } catch (IOException ignored) {
        }
    }

    public CreoDiasODataSource() throws URISyntaxException {
        super(SEARCH_URL);
        setParameterProvider(new CreoDias2ParameterProvider(this));
        this.properties = CreoDiasODataSource.props;
    }

    @Override
    public String defaultId() { return "CreoDIAS New"; }

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
    public Token authenticate() {
        if (credentials == null) {
            throw new QueryException(String.format("Credentials not set for %s", getId()));
        }
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("client_id", "CLOUDFERRO_PUBLIC"));
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
    protected CreoDiasODataQuery createQueryImpl(String sensorName) {
        return new CreoDiasODataQuery(this, sensorName);
    }
}
