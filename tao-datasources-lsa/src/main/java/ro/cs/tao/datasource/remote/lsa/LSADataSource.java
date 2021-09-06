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

package ro.cs.tao.datasource.remote.lsa;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.lsa.auth.LSAAuthentication;
import ro.cs.tao.datasource.remote.lsa.parameters.LSAParameterProvider;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class LSADataSource extends URLDataSource<LSADataQuery, Header> {

    private static final Properties props = new Properties();

    private static String url;

    static {
        try {
            props.load(LSADataSource.class.getResourceAsStream("lsa.properties"));
            url = props.getProperty("lsa.search.url");
        } catch (IOException ignored) {
        }
    }

    protected Map<String, String> connectionStrings;
    private LSAAuthentication authenticationStrategy = null;

    public LSADataSource() throws URISyntaxException {
        super(url);
        this.connectionStrings = new HashMap<String, String>() {{
            put("Download", props.getProperty("lsa.download.url"));
        }};
        setParameterProvider(new LSAParameterProvider(this));
    }

    @Override
    public String defaultId() {
        return "LSA Data Center";
    }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(url, "", "");
    }

    @Override
    public String getConnectionString(String sensorName) {
        return this.connectionStrings.get(sensorName);
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public Header authenticate() throws IOException {
        if (credentials == null) {
            throw new IOException("No credentials set");
        }
        if (authenticationStrategy == null) {
            authenticationStrategy = new LSAAuthentication(this.credentials);
        }
        return new BasicHeader(authenticationStrategy.getAuthenticationTokenName(), authenticationStrategy.getAuthenticationTokenValue(this.getProperty(LSAAuthentication.DOWNLOAD_URL_PROPERTY_NAME)));
    }

    @Override
    public ProductFetchStrategy getProductFetchStrategy(String sensorName) {
        return super.getProductFetchStrategy(StringUtils.capitalize(sensorName.replace("-", "")));
    }

    @Override
    protected LSADataQuery createQueryImpl(String sensorName) {
        return new LSADataQuery(this, sensorName);
    }
}
