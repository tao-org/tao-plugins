/*
 * Copyright (C) 2017 CS ROMANIA
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

package ro.cs.tao.datasource.remote.fedeo;

import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.fedeo.parameters.FedEOParameterProvider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Properties;

public class FedEODataSource extends URLDataSource<FedEODataQuery> {

    private static String URL;

    static {
        Properties props = new Properties();
        try {
            props.load(FedEODataSource.class.getResourceAsStream("fedeo.properties"));
            URL = props.getProperty("fedeo.search.url");
            if (!URL.endsWith("/")) {
                URL += "/";
            }
        } catch (IOException ignored) {
        }
    }

    public FedEODataSource() throws URISyntaxException {
        super(URL);
        setParameterProvider(new FedEOParameterProvider(URL + "description.xml"));
    }

    @Override
    public String defaultId() { return "FedEO"; }

    @Override
    protected FedEODataQuery createQueryImpl(String code) {
        return null;
    }
}
