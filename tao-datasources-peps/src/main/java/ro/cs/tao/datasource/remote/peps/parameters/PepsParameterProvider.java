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
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.peps.Collection;
import ro.cs.tao.datasource.remote.peps.download.PepsDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

import java.util.*;

/**
 * @author Cosmin Cara
 */
public class PepsParameterProvider implements ParameterProvider {

    private static final String[] sensors;
    private static final Map<String, Map<ParameterName, DataSourceParameter>> parameters;
    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Sentinel1", "Sentinel2" };
        parameters = Collections.unmodifiableMap(
                new HashMap<String, Map<ParameterName, DataSourceParameter>>() {{
                    put("Sentinel1", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                        put(ParameterName.create("collection", "collection", "Collection"),
                            new DataSourceParameter("collection", String.class, Collection.S1.toString(), true));
                        put(ParameterName.create(CommonParameterNames.PLATFORM, "platform", "Satellite"),
                            new DataSourceParameter("platform", String.class));
                        put(ParameterName.create("instrument", "instrument", "Instrument"),
                            new DataSourceParameter("instrument", String.class));
                        put(ParameterName.create("processingLevel", "processingLevel", "Processing Level"),
                            new DataSourceParameter("processingLevel", String.class));
                        put(ParameterName.create(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type"),
                            new DataSourceParameter("productType", String.class));
                        put(ParameterName.create("sensorMode", "sensorMode", "Sensor Mode"),
                            new DataSourceParameter("sensorMode", String.class));
                        put(ParameterName.create("orbitDirection", "orbitDirection", "Orbit Direction"),
                            new DataSourceParameter("orbitDirection", String.class));
                        put(ParameterName.create("orbit", "orbit", "Absolute Orbit"),
                            new DataSourceParameter("orbit", Short.class));
                        put(ParameterName.create(CommonParameterNames.RELATIVE_ORBIT, "relativeOrbitNumber", "Relative Orbit"),
                            new DataSourceParameter("relativeOrbitNumber", Short.class));
                        put(ParameterName.create("isNrt", "isNrt", "NRT Product"),
                            new DataSourceParameter("isNrt", Boolean.class));
                        put(ParameterName.create(CommonParameterNames.START_DATE, "startDate", "Start Date"),
                            new DataSourceParameter("startDate", Date.class, true));
                        put(ParameterName.create(CommonParameterNames.END_DATE, "completionDate", "End Date"),
                            new DataSourceParameter("completionDate", Date.class));
                        put(ParameterName.create(CommonParameterNames.FOOTPRINT, "box", "Area of Interest"),
                            new DataSourceParameter("box", Polygon2D.class, true));
                        put(ParameterName.create(CommonParameterNames.POLARISATION, "polarisation", "Polarisation"),
                            new DataSourceParameter("polarisation", String.class));
                    }});
                    put("Sentinel2", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                        put(ParameterName.create("collection", "collection", "Collection"),
                            new DataSourceParameter("collection", String.class, true));
                        put(ParameterName.create(CommonParameterNames.PLATFORM, "platform", "Satellite"),
                            new DataSourceParameter("platform", String.class));
                        put(ParameterName.create("instrument", "instrument", "Instrument"),
                            new DataSourceParameter("instrument", String.class));
                        put(ParameterName.create("processingLevel", "processingLevel", "Processing Level"),
                            new DataSourceParameter("processingLevel", String.class));
                        put(ParameterName.create(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type"),
                            new DataSourceParameter("productType", String.class));
                        put(ParameterName.create("sensorMode", "sensorMode", "Sensor Mode"),
                            new DataSourceParameter("sensorMode", String.class));
                        put(ParameterName.create("orbitDirection", "orbitDirection", "Orbit Direction"),
                            new DataSourceParameter("orbitDirection", String.class));
                        put(ParameterName.create("orbit", "orbit", "Absolute Orbit"),
                            new DataSourceParameter("orbit", Short.class));
                        put(ParameterName.create(CommonParameterNames.RELATIVE_ORBIT, "relativeOrbitNumber", "Relative Orbit"),
                            new DataSourceParameter("relativeOrbitNumber", Short.class));
                        put(ParameterName.create("isNrt", "isNrt", "NRT Product"),
                            new DataSourceParameter("isNrt", Boolean.class));
                        put(ParameterName.create(CommonParameterNames.START_DATE, "startDate", "Start Date"),
                            new DataSourceParameter("startDate", Date.class, true));
                        put(ParameterName.create(CommonParameterNames.END_DATE, "completionDate", "End Date"),
                            new DataSourceParameter("completionDate", Date.class));
                        put(ParameterName.create(CommonParameterNames.FOOTPRINT, "box", "Area of Interest"),
                            new DataSourceParameter("box", Polygon2D.class));
                        put(ParameterName.create(CommonParameterNames.CLOUD_COVER, "cloudCover", "Cloud Cover"),
                            new DataSourceParameter("cloudCover", Double.class));
                        put(ParameterName.create(CommonParameterNames.TILE, "tileid", "UTM Tile"),
                            new DataSourceParameter("tileid", String.class));
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
