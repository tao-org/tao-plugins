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
package ro.cs.tao.datasource.remote.aws.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.aws.LandsatCollection;
import ro.cs.tao.datasource.remote.aws.download.Landsat8Strategy;
import ro.cs.tao.datasource.remote.aws.download.Sentinel2Strategy;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.Tuple;

import java.util.*;

/**
 * @author Cosmin Cara
 */
public class AWSParameterProvider implements ParameterProvider {

    private static final String[] sensors;
    private static final Map<String, Map<ParameterName, DataSourceParameter>> parameters;
    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Sentinel2", "Landsat8" };
        parameters = Collections.unmodifiableMap(
                new HashMap<String, Map<ParameterName, DataSourceParameter>>() {{
                    put("Sentinel2", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                        Tuple<ParameterName, DataSourceParameter> parameter =
                                ParameterProvider.createParameter(CommonParameterNames.PLATFORM, "platformName", "Satellite", String.class, "Sentinel-2");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "beginPosition", "Start Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "endPosition", "End Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.TILE, "tileId", "UTM Tile", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "footprint", "Area of Interest", Polygon2D.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.CLOUD_COVER, "cloudcoverpercentage", "Cloud Cover", Double.class, 100.);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.RELATIVE_ORBIT, "relativeOrbitNumber", "Relative Orbit", Short.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                    }});
                    put("Landsat8", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                        Tuple<ParameterName, DataSourceParameter> parameter =
                                ParameterProvider.createParameter(CommonParameterNames.PLATFORM, "platformName", "Satellite", String.class, "Landsat-8");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "sensingStart", "Start Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "sensingEnd", "End Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("path", "path", "Path", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("row", "row", "Row", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.TILE, "row_path", "Row and Path", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "footprint", "Area of Interest", Polygon2D.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.CLOUD_COVER, "cloudcoverpercentage", "Cloud Cover", Double.class, 100.);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("collection", "collection", "Landsat Collection", String.class, LandsatCollection.COLLECTION_1.toString());
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                    }});
                }});
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel2", new Sentinel2Strategy(targetFolder));
                    put("Landsat8", new Landsat8Strategy(targetFolder));
                }});
    }

    @Override
    public Map<String, Map<ParameterName, DataSourceParameter>> getSupportedParameters() {
        return parameters;
    }

    @Override
    public String[] getSupportedSensors() {
        return sensors;
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }
}
