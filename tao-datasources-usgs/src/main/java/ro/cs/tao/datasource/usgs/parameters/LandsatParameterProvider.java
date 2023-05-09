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
package ro.cs.tao.datasource.usgs.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.usgs.USGSDataSource;
import ro.cs.tao.datasource.usgs.download.EcostressStrategy;
import ro.cs.tao.datasource.usgs.download.Landsat8Strategy;
import ro.cs.tao.datasource.usgs.download.ModisStrategy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class LandsatParameterProvider extends AbstractParameterProvider {

    public LandsatParameterProvider(USGSDataSource dataSource) {
        super();
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Landsat9", new Landsat8Strategy(dataSource, targetFolder, new Properties()));
                    put("Landsat8", new Landsat8Strategy(dataSource, targetFolder, new Properties()));
                    put("Landsat7", new Landsat8Strategy(dataSource, targetFolder, new Properties()));
                    put("Landsat5", new Landsat8Strategy(dataSource, targetFolder, new Properties()));
                    put("Landsat4", new Landsat8Strategy(dataSource, targetFolder, new Properties()));
                    put("VIIRS", new Landsat8Strategy(dataSource, targetFolder, new Properties()));
                    put("HYPERION", new Landsat8Strategy(dataSource, targetFolder, new Properties()));
                    put("ECOSTRESS", new EcostressStrategy(dataSource, targetFolder, new Properties()));
                    put("MODIS", new ModisStrategy(dataSource, targetFolder, new Properties()));
                }});
    }

}
