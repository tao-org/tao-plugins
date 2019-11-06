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
import ro.cs.tao.datasource.remote.asf.download.AsfDownloadStrategy;

import java.util.*;

public class ASFParameterProvider extends AbstractParameterProvider {

    private static Map<String, ProductFetchStrategy> productFetchers;

    public ASFParameterProvider() {
        if (productFetchers == null) {
            final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
            productFetchers = Collections.unmodifiableMap(
                    new HashMap<String, ProductFetchStrategy>() {{
                        put("Sentinel1", new AsfDownloadStrategy(targetFolder, new Properties() {{ put("auto.uncompress", "false"); }}));
                        put("ALOS", new AsfDownloadStrategy(targetFolder, new Properties() {{ put("auto.uncompress", "false"); }}));
                    }});
        }
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() {
        return productFetchers;
    }
}
