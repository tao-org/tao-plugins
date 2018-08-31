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
package ro.cs.tao.datasource.remote.usgs;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.usgs.parameters.LandsatParameterProvider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class USGSDataSource extends URLDataSource<Landsat8Query> {
    private static final Properties props;
    private static String URL;
    private static String BASE_URL;

    static {
        props = new Properties();
        try {
            props.load(USGSDataSource.class.getResourceAsStream("usgs.properties"));
            URL = props.getProperty("usgs.search.url");
            BASE_URL = props.getProperty("usgs.base.url");
        } catch (IOException ignored) {
        }
    }

    public USGSDataSource() throws URISyntaxException {
        super(URL);
        setParameterProvider(new LandsatParameterProvider());
        this.alternateConnectionString = BASE_URL;
        this.properties = USGSDataSource.props;
    }

    @Override
    public String defaultId() { return "USGS"; }

    @Override
    protected Landsat8Query createQueryImpl(String code) {
        return new Landsat8Query(this);
    }
}
