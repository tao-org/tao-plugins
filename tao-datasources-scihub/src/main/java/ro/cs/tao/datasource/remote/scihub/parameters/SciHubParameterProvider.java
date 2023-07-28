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
package ro.cs.tao.datasource.remote.scihub.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.AbstractParameterProvider;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel1DownloadStrategy;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel2ArchiveDownloadStrategy;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel2DownloadStrategy;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel3DownloadStrategy;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel5PDownloadStrategy;

import java.util.Collections;
import java.util.HashMap;

/**
 * @author Cosmin Cara
 */
public final class SciHubParameterProvider extends AbstractParameterProvider {

    public SciHubParameterProvider(SciHubDataSource dataSource) {
        super();
        final ConfigurationProvider configurationProvider = ConfigurationManager.getInstance();
        final String targetFolder = configurationProvider.getValue("product.location");
        final boolean downloadExpanded =
                Boolean.parseBoolean(configurationProvider.getValue(SciHubDataSource.class.getSimpleName() + ".expanded.download",
                                                                   "false"));
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1", new Sentinel1DownloadStrategy(dataSource, targetFolder));
                    if (downloadExpanded) {
                        put("Sentinel2", new Sentinel2DownloadStrategy(dataSource, targetFolder));
                    } else {
                        put("Sentinel2", new Sentinel2ArchiveDownloadStrategy(dataSource, targetFolder));
                    }
                    put("Sentinel3", new Sentinel3DownloadStrategy(dataSource, targetFolder));
                    put("Sentinel5P", new Sentinel5PDownloadStrategy(dataSource, targetFolder));
                }});
    }
}
