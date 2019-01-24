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
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel1DownloadStrategy;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel2DownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

import java.util.*;

/**
 * @author Cosmin Cara
 */
public final class SciHubParameterProvider implements ParameterProvider {

    private static String[] sensors;
    private static Map<String, Map<ParameterName, DataSourceParameter>> parameters;
    private static Map<String, ProductFetchStrategy> productFetchers;

    public SciHubParameterProvider() {
        if (sensors == null) {
            sensors = new String[] { "Sentinel1", "Sentinel2" };
        }
        if (parameters == null) {
            parameters = Collections.unmodifiableMap(
                    new HashMap<String, Map<ParameterName, DataSourceParameter>>() {{
                        put("Sentinel1", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                            put(ParameterName.create(CommonParameterNames.PLATFORM, "platformName", "Satellite"),
                                new DataSourceParameter("platformName", String.class, "Sentinel-1"));
                            put(ParameterName.create(CommonParameterNames.START_DATE, "beginPosition", "Start Date"),
                                new DataSourceParameter("beginPosition", Date.class));
                            put(ParameterName.create(CommonParameterNames.END_DATE, "endPosition", "End Date"),
                                new DataSourceParameter("endPosition", Date.class));
                            put(ParameterName.create(CommonParameterNames.FOOTPRINT, "footprint", "Area of Interest"),
                                new DataSourceParameter("footprint", Polygon2D.class));
                            put(ParameterName.create(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type"),
                                new DataSourceParameter("productType", String.class, "SLC"));
                            put(ParameterName.create(CommonParameterNames.POLARISATION, "polarisationMode", "Polarisation"),
                                new DataSourceParameter("polarisationMode", String.class));
                            put(ParameterName.create("sensorOperationalMode", "sensorOperationalMode", "Operational Mode"),
                                new DataSourceParameter("sensorOperationalMode", String.class));
                            put(ParameterName.create(CommonParameterNames.TILE, "relativeOrbitNumber", "Relative Orbit"),
                                new DataSourceParameter("relativeOrbitNumber", String.class));
                        }});
                        put("Sentinel2", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                            put(ParameterName.create(CommonParameterNames.PLATFORM, "platformName", "Satellite"),
                                new DataSourceParameter("platformName", String.class, "Sentinel-2"));
                            put(ParameterName.create(CommonParameterNames.START_DATE, "beginPosition", "Start Date"),
                                new DataSourceParameter("beginPosition", Date.class));
                            put(ParameterName.create(CommonParameterNames.END_DATE, "endPosition", "End Date"),
                                new DataSourceParameter("endPosition", Date.class));
                            put(ParameterName.create(CommonParameterNames.FOOTPRINT, "footprint", "Area of Interest"),
                                new DataSourceParameter("footprint", Polygon2D.class));
                            put(ParameterName.create(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type"),
                                new DataSourceParameter("productType", String.class, "S2MSI1C"));
                            put(ParameterName.create(CommonParameterNames.CLOUD_COVER, "cloudcoverpercentage", "Cloud Cover"),
                                new DataSourceParameter("cloudcoverpercentage", Double.class));
                            put(ParameterName.create(CommonParameterNames.RELATIVE_ORBIT, "relativeOrbitNumber", "Relative Orbit"),
                                new DataSourceParameter("relativeOrbitNumber", String.class));
                            put(ParameterName.create(CommonParameterNames.PRODUCT, "product", "Product Name"),
                                new DataSourceParameter("product", String.class, null, false));
                            put(ParameterName.create(CommonParameterNames.TILE, "tileId", "UTM Tile"),
                                new DataSourceParameter("tileId", String.class));
                        }});
                    }});
        }
        if (productFetchers == null) {
            final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
            productFetchers = Collections.unmodifiableMap(
                    new HashMap<String, ProductFetchStrategy>() {{
                        put("Sentinel1", new Sentinel1DownloadStrategy(targetFolder));
                        put("Sentinel2", new Sentinel2DownloadStrategy(targetFolder));
                    }});
        }
    }

    @Override
    public String[] getSupportedSensors() {
        return sensors;
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() { return productFetchers; }

    @Override
    public Map<String, Map<ParameterName, DataSourceParameter>> getSupportedParameters() {
        return parameters;
    }
}
