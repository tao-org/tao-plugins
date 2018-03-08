/*
 * Copyright (C) 2017 CS ROMANIA
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
import ro.cs.tao.datasource.param.ParameterDescriptor;
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
    private static final Map<String, Map<String, ParameterDescriptor>> parameters;
    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Sentinel1", "Sentinel2" };
        parameters = Collections.unmodifiableMap(
                new HashMap<String, Map<String, ParameterDescriptor>>() {{
                    put("Sentinel1", new HashMap<String, ParameterDescriptor>() {{
                        put("collection", new ParameterDescriptor("collection", String.class, Collection.S1.toString(), true));
                        put("platform", new ParameterDescriptor("platform", String.class));
                        put("instrument", new ParameterDescriptor("instrument", String.class));
                        put("processingLevel",  new ParameterDescriptor("processingLevel", String.class));
                        put("productType",  new ParameterDescriptor("productType", String.class));
                        put("sensorMode",  new ParameterDescriptor("sensorMode", String.class));
                        put("orbitDirection",  new ParameterDescriptor("orbitDirection", String.class));
                        put("orbit",  new ParameterDescriptor("orbit", Short.class));
                        put("relativeOrbitNumber",  new ParameterDescriptor("relativeOrbitNumber", Short.class));
                        put("isNrt",  new ParameterDescriptor("isNrt", Boolean.class));
                        put("startDate",  new ParameterDescriptor("startDate", Date.class, true));
                        put("completionDate",  new ParameterDescriptor("completionDate", Date.class));
                        put("box",  new ParameterDescriptor("box", Polygon2D.class, true));
                        put("polarisation",  new ParameterDescriptor("polarisation", String.class));
                    }});
                    put("Sentinel2", new HashMap<String, ParameterDescriptor>() {{
                        put("collection", new ParameterDescriptor("collection", String.class, true));
                        put("platform", new ParameterDescriptor("platform", String.class));
                        put("instrument", new ParameterDescriptor("instrument", String.class));
                        put("processingLevel",  new ParameterDescriptor("processingLevel", String.class));
                        put("productType",  new ParameterDescriptor("productType", String.class));
                        put("sensorMode",  new ParameterDescriptor("sensorMode", String.class));
                        put("orbitDirection",  new ParameterDescriptor("orbitDirection", String.class));
                        put("orbit",  new ParameterDescriptor("orbit", Short.class));
                        put("relativeOrbitNumber",  new ParameterDescriptor("relativeOrbitNumber", Short.class));
                        put("isNrt",  new ParameterDescriptor("isNrt", Boolean.class));
                        put("startDate",  new ParameterDescriptor("startDate", Date.class, true));
                        put("completionDate",  new ParameterDescriptor("completionDate", Date.class));
                        put("box",  new ParameterDescriptor("box", Polygon2D.class));
                        put("cloudCover",  new ParameterDescriptor("cloudCover", Double.class));
                        put("tileid", new ParameterDescriptor("tileid", String.class));
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
    public Map<String, Map<String, ParameterDescriptor>> getSupportedParameters() {
        return parameters;
    }

    @Override
    public String[] getSupportedSensors() {
        return sensors;
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }

}
