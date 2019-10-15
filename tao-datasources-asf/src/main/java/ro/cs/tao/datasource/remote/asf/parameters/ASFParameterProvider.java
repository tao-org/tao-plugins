package ro.cs.tao.datasource.remote.asf.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.SimpleArchiveDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.Tuple;

import java.util.*;

public class ASFParameterProvider implements ParameterProvider {

    @Override
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        return Collections.unmodifiableMap(
                new HashMap<String, Map<String, DataSourceParameter>>() {{
                    put("Sentinel1", new LinkedHashMap<String, DataSourceParameter>() {{
                        Tuple<String, DataSourceParameter> parameter =
                                ParameterProvider.createParameter(CommonParameterNames.PLATFORM, "platform", "Satellite", String.class, "Sentinel-1");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "start", "Start Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "end", "End Date", Date.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "intersectsWith", "Area of Interest", Polygon2D.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type", String.class, "SLC",
                                                                      false, "METADATA_GRD", "GRD_HS", "GRD_HD", "GRD_MS", "GRD_MD", "GRD_FS", "GRD_FD", "SLC",
                                                                      "RAW", "OCN", "METADATA_RAW", "METADATA", "METADATA_SLC", "THUMBNAIL");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.POLARISATION, "polarisation", "Polarisation", String.class, "VV+VH",
                                                                      false, "HH", "VV", "Dual VV", "Dual HV", "Dual VH", "HH+HV", "VV+VH");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter(CommonParameterNames.TILE, "relativeOrbit", "Relative Orbit", String.class);
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                        parameter = ParameterProvider.createParameter("beamMode", "beamMode", "Operational Mode", String.class,
                                                                      "IW", false, "S1", "S2", "S3", "S4", "S5", "S6", "IW", "EW", "WV");
                        put(parameter.getKeyOne(), parameter.getKeyTwo());
                    }});
                }});
    }

    public String[] getSupportedSensors() {
        // return new String[] { "ALOS", "AIRSAR", "ERS-1", "ERS-2", "JERS-1", "RADARSAT-1", "SEASAT", "Sentinel-1", "SMAP", "UAVSAR" };
        return new String[] { "Sentinel-1" };
    }

    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() {
        return Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Sentinel1",
                        new SimpleArchiveDownloadStrategy(ConfigurationManager.getInstance().getValue("product.location"),
                                                          new Properties() {{ put("auto.uncompress", "false"); }}));
                }});
    }
}
