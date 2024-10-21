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

import org.apache.commons.lang3.StringUtils;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.usgs.USGSDataSource;
import ro.cs.tao.datasource.usgs.download.USGSDownloadStrategy;

import java.util.HashMap;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class USGSParameterProvider extends AbstractParameterProvider {

    public USGSParameterProvider(USGSDataSource dataSource) {
        super();
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        this.productFetchers = new HashMap<>();
        final String[] sensors = getSupportedSensors();
        for (String sensor : sensors) {
            this.productFetchers.put(StringUtils.capitalize(sensor.replaceAll("-", "")), new USGSDownloadStrategy(dataSource, targetFolder, new Properties()));
        }
    }

}
