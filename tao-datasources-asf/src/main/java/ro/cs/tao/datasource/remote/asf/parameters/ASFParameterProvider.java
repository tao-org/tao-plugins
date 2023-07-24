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
package ro.cs.tao.datasource.remote.asf.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.asf.ASFDataSource;
import ro.cs.tao.datasource.remote.asf.download.AsfDownloadStrategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

public class ASFParameterProvider extends AbstractParameterProvider {

    public ASFParameterProvider(ASFDataSource dataSource) {
        super();
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1", new AsfDownloadStrategy(dataSource, targetFolder, new Properties() {{ put("auto.uncompress", "false"); }}));
                    put("ALOS", new AsfDownloadStrategy(dataSource, targetFolder, new Properties() {{ put("auto.uncompress", "false"); }}));
                    put("SMAP", new AsfDownloadStrategy(dataSource, targetFolder, new Properties() {{ put("auto.uncompress", "false"); }}));
                }});
    }
}
