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
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.peps.Collection;
import ro.cs.tao.datasource.remote.peps.download.PepsDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
                    put("Sentinel1", new HashMap<String, DataSourceParameter>() {{
                        put("collection", new DataSourceParameter("collection", String.class, Collection.S1.toString(), true));
                        put("platform", new DataSourceParameter("platform", String.class));
                        put("instrument", new DataSourceParameter("instrument", String.class));
                        put("processingLevel",  new DataSourceParameter("processingLevel", String.class));
                        put("productType",  new DataSourceParameter("productType", String.class));
                        put("sensorMode",  new DataSourceParameter("sensorMode", String.class));
                        put("orbitDirection",  new DataSourceParameter("orbitDirection", String.class));
                        put("orbit",  new DataSourceParameter("orbit", Short.class));
                        put("relativeOrbitNumber",  new DataSourceParameter("relativeOrbitNumber", Short.class));
                        put("isNrt",  new DataSourceParameter("isNrt", Boolean.class));
                        put("startDate",  new DataSourceParameter("startDate", Date.class, true));
                        put("completionDate",  new DataSourceParameter("completionDate", Date.class));
                        put("box",  new DataSourceParameter("box", Polygon2D.class, true));
                        put("polarisation",  new DataSourceParameter("polarisation", String.class));
                    }});
                    put("Sentinel2", new HashMap<String, DataSourceParameter>() {{
                        put("collection", new DataSourceParameter("collection", String.class, true));
                        put("platform", new DataSourceParameter("platform", String.class));
                        put("instrument", new DataSourceParameter("instrument", String.class));
                        put("processingLevel",  new DataSourceParameter("processingLevel", String.class));
                        put("productType",  new DataSourceParameter("productType", String.class));
                        put("sensorMode",  new DataSourceParameter("sensorMode", String.class));
                        put("orbitDirection",  new DataSourceParameter("orbitDirection", String.class));
                        put("orbit",  new DataSourceParameter("orbit", Short.class));
                        put("relativeOrbitNumber",  new DataSourceParameter("relativeOrbitNumber", Short.class));
                        put("isNrt",  new DataSourceParameter("isNrt", Boolean.class));
                        put("startDate",  new DataSourceParameter("startDate", Date.class, true));
                        put("completionDate",  new DataSourceParameter("completionDate", Date.class));
                        put("box",  new DataSourceParameter("box", Polygon2D.class));
                        put("cloudCover",  new DataSourceParameter("cloudCover", Double.class));
                        put("tileid", new DataSourceParameter("tileid", String.class));
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
