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
import ro.cs.tao.datasource.param.DataSourceParameter;
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
    private static Map<String, Map<String, DataSourceParameter>> parameters;
    private static Map<String, ProductFetchStrategy> productFetchers;

    public SciHubParameterProvider() {
        if (sensors == null) {
            sensors = new String[] { "Sentinel1", "Sentinel2" };
        }
        if (parameters == null) {
            parameters = Collections.unmodifiableMap(
                    new HashMap<String, Map<String, DataSourceParameter>>() {{
                        put("Sentinel1", new LinkedHashMap<String, DataSourceParameter>() {{
                            put("platformName", new DataSourceParameter("platformName", String.class, "Sentinel-1"));
                            put("beginPosition", new DataSourceParameter("beginPosition", Date.class));
                            put("endPosition", new DataSourceParameter("endPosition", Date.class));
                            put("footprint", new DataSourceParameter("footprint", Polygon2D.class));
                            put("productType", new DataSourceParameter("productType", String.class, "SLC"));
                            put("polarisationMode", new DataSourceParameter("polarisationMode", String.class));
                            put("sensorOperationalMode", new DataSourceParameter("sensorOperationalMode", String.class));
                            put("relativeOrbitNumber", new DataSourceParameter("relativeOrbitNumber", String.class));
                        }});
                        put("Sentinel2", new LinkedHashMap<String, DataSourceParameter>() {{
                            put("platformName", new DataSourceParameter("platformName", String.class, "Sentinel-2"));
                            put("beginPosition", new DataSourceParameter("beginPosition", Date.class));
                            put("endPosition", new DataSourceParameter("endPosition", Date.class));
                            put("footprint", new DataSourceParameter("footprint", Polygon2D.class));
                            put("productType", new DataSourceParameter("productType", String.class, "S2MSI1C"));
                            put("cloudcoverpercentage", new DataSourceParameter("cloudcoverpercentage", Double.class, 100.));
                            put("relativeOrbitNumber", new DataSourceParameter("relativeOrbitNumber", Short.class));
                            put("product", new DataSourceParameter("product", String.class, null, false));
                            put("tileId", new DataSourceParameter("tileId", String.class));
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
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        return parameters;
    }
}
