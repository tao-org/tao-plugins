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
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.SimpleArchiveDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.Tuple;

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
                            Tuple<ParameterName, DataSourceParameter> parameter =
                                    ParameterProvider.createParameter(CommonParameterNames.PLATFORM, "datasetName", "Dataset", String.class, "LANDSAT_8_C1");
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "startDate", "Start Date", Date.class);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "endDate", "End Date", Date.class);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "spatialFilter", "Area of Interest", Polygon2D.class);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter("minClouds", "minCloudCover", "Minimum Cloud Cover", Integer.class, 0);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter("maxClouds", "maxCloudCover", "Maximum Cloud Cover", Integer.class, 100);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter("row", "WRS Row", "Row", Integer.class);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter("path", "WRS Path", "Path", Integer.class);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter(CommonParameterNames.TILE, "row_path", "Row and Path", String[].class);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT, "Landsat Product Identifier", "Product", String.class);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "Data Type Level-1", "Product Type", String.class, "L1TP",
                                                                          false, "L1TP", "L1GT", "L1GS");
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                        }});
                    }});
        }
        if (productFetchers == null) {
            final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
            productFetchers = Collections.unmodifiableMap(
                    new HashMap<String, ProductFetchStrategy>() {{
                        put("Landsat8", new SimpleArchiveDownloadStrategy(targetFolder, new Properties()));
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
