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

package ro.cs.tao.datasource.remote.eocat;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.eocat.auth.EOCATAuthentication;
import ro.cs.tao.datasource.remote.eocat.parameters.EOCATParameterProvider;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

public class EOCATDataSource extends URLDataSource<EOCATDataQuery, Header> {

    private static String url;

    static {
        Properties props = new Properties();
        try {
            props.load(EOCATDataSource.class.getResourceAsStream("eocat.properties"));
            url = props.getProperty("eocat.search.url");
            if (!url.endsWith("/")) {
                url += "/";
            }
        } catch (IOException ignored) {
        }
    }

    private EOCATAuthentication authenticationStrategy = null;

    public EOCATDataSource() throws URISyntaxException {
        super(url);
        setParameterProvider(new EOCATParameterProvider(this));
    }

    @Override
    public String defaultId() {
        return "EO-CAT";
    }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(url, "", "");
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
            authenticationStrategy = new EOCATAuthentication(this.credentials);
        }
        return new BasicHeader(authenticationStrategy.getAuthenticationTokenName(), authenticationStrategy.getAuthenticationTokenValue(this.getProperty(EOCATAuthentication.DOWNLOAD_URL_PROPERTY_NAME)));
    }

    @Override
    public ProductFetchStrategy getProductFetchStrategy(String sensorName) {
        return super.getProductFetchStrategy(StringUtils.capitalize(sensorName.replace("-", "")));
    }

    @Override
    protected EOCATDataQuery createQueryImpl(String sensorName) {
        return new EOCATDataQuery(this, sensorName);
    }
}
