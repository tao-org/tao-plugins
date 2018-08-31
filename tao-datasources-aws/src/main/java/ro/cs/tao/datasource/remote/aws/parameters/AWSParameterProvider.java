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
import ro.cs.tao.datasource.param.ParameterDescriptor;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.aws.LandsatCollection;
import ro.cs.tao.datasource.remote.aws.download.Landsat8Strategy;
import ro.cs.tao.datasource.remote.aws.download.Sentinel2Strategy;
import ro.cs.tao.eodata.Polygon2D;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Cosmin Cara
 */
public class AWSParameterProvider implements ParameterProvider {

    private static final String[] sensors;
    private static final Map<String, Map<String, ParameterDescriptor>> parameters;
    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Sentinel2", "Landsat8" };
        parameters = Collections.unmodifiableMap(
                new HashMap<String, Map<String, ParameterDescriptor>>() {{
                    put("Sentinel2", new HashMap<String, ParameterDescriptor>() {{
                        put("platformName", new ParameterDescriptor("platformName", String.class, "Sentinel-2", true));
                        put("beginPosition",  new ParameterDescriptor("beginPosition", Date.class));
                        put("endPosition",  new ParameterDescriptor("endPosition", Date.class));
                        put("tileId", new ParameterDescriptor("tileId", String.class));
                        put("footprint",  new ParameterDescriptor("footprint", Polygon2D.class));
                        put("productType",  new ParameterDescriptor("productType", String.class));
                        put("cloudcoverpercentage",  new ParameterDescriptor("cloudcoverpercentage", Double.class, 100.));
                        put("relativeOrbitNumber",  new ParameterDescriptor("relativeOrbitNumber", Short.class));
                    }});
                    put("Landsat8", new HashMap<String, ParameterDescriptor>() {{
                        put("platformName", new ParameterDescriptor("platformName", String.class, "Landsat-8", true));
                        put("sensingStart", new ParameterDescriptor("sensingStart", Date.class));
                        put("sensingEnd", new ParameterDescriptor("sensingEnd", Date.class));
                        put("path", new ParameterDescriptor("path", String.class));
                        put("row", new ParameterDescriptor("row", String.class));
                        put("row_path", new ParameterDescriptor("row_path", String.class));
                        put("footprint",  new ParameterDescriptor("footprint", Polygon2D.class));
                        put("cloudcoverpercentage", new ParameterDescriptor("cloudcoverpercentage", Double.class, 100.));
                        put("productType", new ParameterDescriptor("productType", String.class));
                        put("collection", new ParameterDescriptor("collection", String.class, LandsatCollection.COLLECTION_1.toString()));
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
