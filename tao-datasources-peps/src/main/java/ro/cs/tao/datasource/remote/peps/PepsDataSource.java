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
package ro.cs.tao.datasource.remote.peps;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.peps.parameters.PepsParameterProvider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class PepsDataSource extends URLDataSource<PepsDataQuery> {
    private static String URL;

    static {
        Properties props = new Properties();
        try {
            props.load(PepsDataSource.class.getResourceAsStream("peps.properties"));
            URL = props.getProperty("peps.search.url");
        } catch (IOException ignored) {
        }
    }

    public PepsDataSource() throws URISyntaxException {
        super(URL);
        setParameterProvider(new PepsParameterProvider());
    }

    @Override
    public String defaultId() { return "PEPS"; }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public void setCredentials(String username, String password) {
        super.setCredentials(username, password);
    }

    @Override
    protected PepsDataQuery createQueryImpl(String sensorName) {
        return new PepsDataQuery(this, sensorName);
    }
}
