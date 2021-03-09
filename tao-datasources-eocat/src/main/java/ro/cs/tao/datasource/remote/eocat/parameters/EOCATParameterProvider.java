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

package ro.cs.tao.datasource.remote.eocat.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.opensearch.OpenSearchParameterProvider;
import ro.cs.tao.datasource.remote.eocat.EOCATDataSource;
import ro.cs.tao.datasource.remote.eocat.download.EOCATDownloadStrategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class EOCATParameterProvider extends OpenSearchParameterProvider {

    public EOCATParameterProvider(EOCATDataSource dataSource, String url) {
        super(url);
        this.endpointType = "application/atom+xml";
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        this.productFetchers = new HashMap<>();
        String[] sensors = getSupportedSensors();
        for (String sensor : sensors) {
            this.productFetchers.put(sensor, new EOCATDownloadStrategy(dataSource, targetFolder, new Properties()));
        }
    }

    @Override
    protected String sensorParameterName() {
        return "platform";
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() {
        return this.productFetchers;
    }
}
