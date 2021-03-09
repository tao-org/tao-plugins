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
package ro.cs.tao.datasource.remote.scihub;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.scihub.parameters.SciHubParameterProvider;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class SciHubDataSource extends URLDataSource<SciHubDataQuery, String> {
    private static final Properties props;
    private static String APIHUB_URL;
    private static String DHUS_URL;

    static {
        props = new Properties();
        try {
            props.load(SciHubDataSource.class.getResourceAsStream("scihub.properties"));
            APIHUB_URL = props.getProperty("scihub.apihub.url");
            DHUS_URL = props.getProperty("scihub.dhus.url");
        } catch (IOException ignored) {
        }
    }

    public SciHubDataSource() throws URISyntaxException {
        super(APIHUB_URL);
        this.alternateConnectionString = DHUS_URL;
        setParameterProvider(new SciHubParameterProvider(this));
        this.properties = SciHubDataSource.props;
    }

    @Override
    public String defaultId() { return "Scientific Data Hub"; }

    @Override
    public int getMaximumAllowedTransfers() { return 2; }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(APIHUB_URL, credentials.getUserName(), credentials.getPassword());
    }

    @Override
    public boolean requiresAuthentication() { return true; }

    @Override
    public void setCredentials(String username, String password) {
        super.setCredentials(username, password);
    }

    @Override
    public String authenticate() throws IOException {
        if (credentials == null) {
            throw new IOException("No credentials set");
        }
        return NetUtils.getAuthToken(credentials.getUserName(), credentials.getPassword());
    }

    @Override
    protected SciHubDataQuery createQueryImpl(String sensorName) {
        return new SciHubDataQuery(this, sensorName);
    }
}
