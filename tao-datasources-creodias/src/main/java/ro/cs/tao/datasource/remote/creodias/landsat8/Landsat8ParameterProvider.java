package ro.cs.tao.datasource.remote.creodias.landsat8;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.remote.NoDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

import java.util.*;

public class Landsat8ParameterProvider implements ParameterProvider {

    private static final String[] sensors;
    private static final Map<String, Map<ParameterName, DataSourceParameter>> parameters;
    private static final Map<String, ProductFetchStrategy> productFetchers;

    static {
        sensors = new String[] { "Landsat8" };
        parameters = Collections.unmodifiableMap(
                new HashMap<String, Map<ParameterName, DataSourceParameter>>() {{
                    put("Landsat8", new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                        put(ParameterName.create("processingLevel", "processingLevel", "Processing Level"),
                            new DataSourceParameter("processingLevel", String.class, "LEVEL1T", false,
                                                    "LEVEL1GT", "LEVEL1T", "LEVEL1TP"));
                        put(ParameterName.create(CommonParameterNames.PRODUCT_TYPE, "productType", "Product Type"),
                            new DataSourceParameter("productType", String.class, "L1T", false,
                                                    "L1T", "L1GT", "L1TP"));
                        put(ParameterName.create("path", "path", "Path"),
                            new DataSourceParameter("path", Short.class));
                        put(ParameterName.create("row", "row", "Row"),
                            new DataSourceParameter("row", Short.class));
                        put(ParameterName.create(CommonParameterNames.START_DATE, "startDate", "Start Date"),
                            new DataSourceParameter("startDate", Date.class, true));
                        put(ParameterName.create(CommonParameterNames.END_DATE, "completionDate", "End Date"),
                            new DataSourceParameter("completionDate", Date.class));
                        put(ParameterName.create(CommonParameterNames.FOOTPRINT, "geometry", "Area of Interest"),
                            new DataSourceParameter("geometry", Polygon2D.class, true));
                        put(ParameterName.create(CommonParameterNames.CLOUD_COVER, "cloudCover", "Cloud Cover"),
                            new DataSourceParameter("cloudCover", Short.class));
                        put(ParameterName.create(CommonParameterNames.PRODUCT, "productIdentifier", "Product Name"),
                            new DataSourceParameter("productIdentifier", String.class));
                    }});
                }});
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        productFetchers = Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    put("Landsat8", new NoDownloadStrategy(targetFolder, new Properties()));
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
