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
package ro.cs.tao.datasource.remote.peps.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.peps.Collection;
import ro.cs.tao.datasource.remote.peps.download.PepsDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.Tuple;

import java.util.*;

/**
 * @author Cosmin Cara
 */
public class PepsParameterProvider implements ParameterProvider {

    private static final String[] sensors;
    private static final Map<String, Map<String, DataSourceParameter>> parameters;
    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Sentinel1", "Sentinel2" };
        parameters = Collections.unmodifiableMap(
                new HashMap<String, Map<String, DataSourceParameter>>() {{
                    put("Sentinel1", new LinkedHashMap<String, DataSourceParameter>() {{
                        Tuple<String, DataSourceParameter> parameter =
                                ParameterProvider.createParameter("collection", "collection", "Collection", String.class, Collection.S1.toString());
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PLATFORM, "platform", "Satellite", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("instrument", "instrument", "Instrument", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("processingLevel", "processingLevel", "Processing Level", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("sensorMode", "sensorMode", "Sensor Mode", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("orbitDirection", "orbitDirection", "Orbit Direction", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("orbit", "orbit", "Absolute Orbit", Short.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.RELATIVE_ORBIT, "relativeOrbitNumber", "Relative Orbit", Short.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("isNrt", "isNrt", "NRT Product", Boolean.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "startDate", "Start Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "completionDate", "End Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "box", "Area of Interest", Polygon2D.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.POLARISATION, "polarisation", "Polarisation", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                    }});
                    put("Sentinel2", new LinkedHashMap<String, DataSourceParameter>() {{
                        Tuple<String, DataSourceParameter> parameter =
                                ParameterProvider.createParameter("collection", "collection", "Collection", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PLATFORM, "platform", "Satellite", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("instrument", "instrument", "Instrument", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("processingLevel", "processingLevel", "Processing Level", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("sensorMode", "sensorMode", "Sensor Mode", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("orbitDirection", "orbitDirection", "Orbit Direction", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("orbit", "orbit", "Absolute Orbit", Short.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.RELATIVE_ORBIT, "relativeOrbitNumber", "Relative Orbit", Short.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("isNrt", "isNrt", "NRT Product", Boolean.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "startDate", "Start Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "completionDate", "End Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "box", "Area of Interest", Polygon2D.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.CLOUD_COVER, "cloudCover", "Cloud Cover", Double.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.TILE, "tileid", "UTM Tile", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                    }});
                }});
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1", new PepsDownloadStrategy(targetFolder));
                    put("Sentinel2", new PepsDownloadStrategy(targetFolder));
                }});
    }

    @Override
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        return parameters;
    }

    @Override
    public String[] getSupportedSensors() {
        return sensors;
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }

}
