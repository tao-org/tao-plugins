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
package ro.cs.tao.datasource.remote.odata.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductPathBuilder;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.odata.CreoDiasODataSource;
import ro.cs.tao.datasource.remote.odata.download.CreoDIASDownloadStrategy;
import ro.cs.tao.datasource.remote.odata.download.CreoDIASPathBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public final class CreoDias2ParameterProvider extends AbstractParameterProvider {

    public CreoDias2ParameterProvider(CreoDiasODataSource dataSource) {
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        Properties properties = new Properties();
        properties.put(ProductPathBuilder.PATH_BUILDER_CLASS, CreoDIASPathBuilder.class.getName());
        CreoDIASDownloadStrategy strategy = new CreoDIASDownloadStrategy(dataSource, targetFolder, properties);
        productFetchers = Collections.unmodifiableMap(
                new HashMap<>() {{
                    put("Sentinel1", strategy);
                    put("Sentinel2", strategy);
                    put("Sentinel3", strategy);
                    put("Sentinel5P", strategy);
                    put("Landsat8", strategy);
                }});
    }
}
