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
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel1DownloadStrategy;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel2ArchiveDownloadStrategy;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel2DownloadStrategy;
import ro.cs.tao.datasource.remote.scihub.download.Sentinel3DownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.Tuple;

import java.util.*;

/**
 * @author Cosmin Cara
 */
public final class SciHubParameterProvider implements ParameterProvider {

    private static String[] sensors;
    private static Map<String, Map<ParameterName, DataSourceParameter>> parameters;
    private static Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Sentinel1", "Sentinel2" , "Sentinel3" };
        parameters = Collections.unmodifiableMap(
                new HashMap<String, Map<ParameterName, DataSourceParameter>>() {{
                    put("Sentinel1", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                        Tuple<ParameterName, DataSourceParameter> parameter =
                                ParameterProvider.createParameter(CommonParameterNames.PLATFORM, "platformName", "Satellite", String.class, "Sentinel-1");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "beginPosition", "Start Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "endPosition", "End Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "footprint", "Area of Interest", Polygon2D.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type", String.class, "SLC",
                                                                      false, "SLC", "GRD", "OCN");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.POLARISATION, "polarisationMode", "Polarisation", String.class, "VV+VH",
                                                                      false, "HH", "VV", "HV", "VH", "HH+HV", "VV+VH");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("sensorOperationalMode", "sensorOperationalMode", "Operational Mode", String.class,
                                                                      "IW", false, "SM", "IW", "EW", "WV");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.TILE, "relativeOrbitNumber", "Relative Orbit", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                    }});
                    put("Sentinel2", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                        Tuple<ParameterName, DataSourceParameter> parameter =
                                ParameterProvider.createParameter(CommonParameterNames.PLATFORM, "platformName", "Satellite", String.class, "Sentinel-2");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "beginPosition", "Start Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "endPosition", "End Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "footprint", "Area of Interest", Polygon2D.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type", String.class, "S2MSI1C",
                                                                      false, "S2MSI1C", "S2MSI2A", "S2MSI2Ap");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.CLOUD_COVER, "cloudcoverpercentage", "Cloud Cover", Double.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.RELATIVE_ORBIT, "relativeOrbitNumber", "Relative Orbit", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT, "product", "Product Name", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.TILE, "tileId", "UTM Tile", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                    }});
                    put("Sentinel3", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                        Tuple<ParameterName, DataSourceParameter> parameter =
                                ParameterProvider.createParameter(CommonParameterNames.PLATFORM, "platformName", "Satellite", String.class, "Sentinel-3");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "beginPosition", "Start Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "endPosition", "End Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "footprint", "Area of Interest", Polygon2D.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type", String.class, null,
                                                                      false, "OL_1_EFR___", "OL_1_ERR___", "OL_2_LFR___", "OL_2_LRR___",
                                                                      "SR_1_SRA___", "SR_1_SRA_A_", "SR_1_SRA_BS", "SR_2_LAN___",
                                                                      "SL_1_RBT___", "SL_2_LST___",
                                                                      "SY_2_SYN___", "SY_2_V10___", "SY_2_VG1___", "SY_2_VGP___");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("timeliness", "timeliness", "Timeliness", String.class, null,
                                                                      false, "Near Real Time", "Short Time Critical", "Non Time Critical");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.RELATIVE_ORBIT, "relativeOrbitNumber", "Relative Orbit", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT, "product", "Product Name", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("instrumentshortname", "instrumentshortname", "Instrument", String.class, null,
                                                                      false, "OLCI", "SRAL", "SLSTR", "SYNERGY");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("productlevel", "productlevel", "Product Level", String.class, null, false, "L1", "L2");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                    }});
                }});
        ConfigurationManager configurationManager = ConfigurationManager.getInstance();
        final String targetFolder = configurationManager.getValue("product.location");
        final boolean downloadExpanded =
                Boolean.parseBoolean(configurationManager.getValue(SciHubDataSource.class.getSimpleName() + ".expanded.download",
                                                                   "false"));
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1", new Sentinel1DownloadStrategy(targetFolder));
                    if (downloadExpanded) {
                        put("Sentinel2", new Sentinel2DownloadStrategy(targetFolder));
                    } else {
                        put("Sentinel2", new Sentinel2ArchiveDownloadStrategy(targetFolder));
                    }
                    put("Sentinel3", new Sentinel3DownloadStrategy(targetFolder));
                }});
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
