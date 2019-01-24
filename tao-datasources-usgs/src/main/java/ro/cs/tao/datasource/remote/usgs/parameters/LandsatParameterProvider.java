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
package ro.cs.tao.datasource.remote.usgs.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.usgs.download.LandsatDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

import java.util.*;

/**
 * @author Cosmin Cara
 */
public class LandsatParameterProvider implements ParameterProvider {

    private static String[] sensors;
    private static Map<String, Map<ParameterName, DataSourceParameter>> parameters;
    private static Map<String, ProductFetchStrategy> productFetchers;

    public LandsatParameterProvider() {
        if (sensors == null) {
            sensors = new String[] { "Landsat8" };
        }
        if (parameters == null) {
            parameters = Collections.unmodifiableMap(
                    new HashMap<String, Map<ParameterName, DataSourceParameter>>() {{
                        put("Landsat8", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                            put(ParameterName.create(CommonParameterNames.PLATFORM, "satellite_name", "Satellite"),
                                                     new DataSourceParameter("satellite_name", String.class, "landsat-8"));
                            put(ParameterName.create(CommonParameterNames.START_DATE, "date_from", "Start Date"),
                                new DataSourceParameter("date_from", Date.class));
                            put(ParameterName.create(CommonParameterNames.END_DATE, "date_to", "End Date"),
                                new DataSourceParameter("date_to", Date.class));
                            put(ParameterName.create(CommonParameterNames.FOOTPRINT, "footprint", "Area of Interest"),
                                new DataSourceParameter("footprint", Polygon2D.class));
                            put(ParameterName.create("minClouds", "cloud_from", "Minimum Cloud Cover"),
                                new DataSourceParameter("cloud_from", Double.class, 0.));
                            put(ParameterName.create("maxClouds", "cloud_to", "Maximum Cloud Cover"),
                                new DataSourceParameter("cloud_to", Double.class, 100.));
                            put(ParameterName.create("row", "row", "Row"),
                                new DataSourceParameter("row", Integer.class));
                            put(ParameterName.create("path", "path", "Path"),
                                new DataSourceParameter("path", Integer.class));
                            put(ParameterName.create(CommonParameterNames.TILE, "row_path", "Row and Path"),
                                new DataSourceParameter("row_path", String[].class));
                        }});
                    }});
        }
        if (productFetchers == null) {
            final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
            productFetchers = Collections.unmodifiableMap(
                    new HashMap<String, ProductFetchStrategy>() {{
                        put("Landsat8", new LandsatDownloadStrategy(targetFolder));
                    }});
        }
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }

    @Override
    public Map<String, Map<ParameterName, DataSourceParameter>> getSupportedParameters() {
        return parameters;
    }

    @Override
    public String[] getSupportedSensors() {
        return sensors;
    }
}
