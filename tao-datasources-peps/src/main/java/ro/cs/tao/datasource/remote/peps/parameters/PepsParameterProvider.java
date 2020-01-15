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
package ro.cs.tao.datasource.remote.peps.parameters;

import ro.cs.tao.configuration.Configuration;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.peps.PepsDataSource;
import ro.cs.tao.datasource.remote.peps.download.PepsDownloadStrategy;

import java.util.Collections;
import java.util.HashMap;

/**
 * @author Cosmin Cara
 */
public class PepsParameterProvider extends AbstractParameterProvider {

    public PepsParameterProvider(PepsDataSource dataSource) {
        super();
        final String targetFolder = ConfigurationManager.getInstance().getValue(Configuration.FileSystem.PRODUCTS_LOCATION);
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1", new PepsDownloadStrategy(dataSource, targetFolder));
                    put("Sentinel2", new PepsDownloadStrategy(dataSource, targetFolder));
                }});
    }
}
