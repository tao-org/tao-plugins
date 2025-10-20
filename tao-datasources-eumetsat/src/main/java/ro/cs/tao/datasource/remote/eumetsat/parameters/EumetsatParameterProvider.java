package ro.cs.tao.datasource.remote.eumetsat.parameters;

import org.apache.commons.lang3.StringUtils;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.opensearch.OpenSearchParameterProvider;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.remote.eumetsat.EumetsatDataSource;
import ro.cs.tao.datasource.remote.eumetsat.download.EumetsatDownloadStrategy;
import ro.cs.tao.eodata.Polygon2D;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class EumetsatParameterProvider extends OpenSearchParameterProvider {

    private static Map<String, CollectionDescription> sensorTypes;
    private final EumetsatDataSource source;
    private Map<String, ProductFetchStrategy> productFetchers;

    public EumetsatParameterProvider(EumetsatDataSource source) {
        super(source.getConnectionString() + "osdd");
        this.source = source;
        final String targetFolder = ConfigurationManager.getInstance().getValue("product.location");
        this.productFetchers = new HashMap<>();
        String[] sensors = getSupportedSensors();
        for (String sensor : sensors) {
            this.productFetchers.put(StringUtils.capitalize(sensor.replace("-", "")),
                                     new EumetsatDownloadStrategy(source, targetFolder, new Properties()));
        }
    }

    @Override
    protected String sensorParameterName() {
        return "pi";
    }

    @Override
    public Map<String, CollectionDescription> getSensorTypes() {
        return Map.of();
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() {
        return this.productFetchers;
    }

    @Override
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        Map<String, Map<String, DataSourceParameter>> supportedParameters = super.getSupportedParameters();
        if (supportedParameters.values().stream().noneMatch(map -> map.containsKey(CommonParameterNames.START_DATE))) {
            for (Map<String, DataSourceParameter> map : supportedParameters.values()) {
                map.put(CommonParameterNames.START_DATE,
                        new DataSourceParameter(CommonParameterNames.START_DATE, "dtstart", LocalDateTime.class, "Start Date"));
                map.put(CommonParameterNames.END_DATE,
                        new DataSourceParameter(CommonParameterNames.END_DATE, "dtend", LocalDateTime.class, "End Date"));
                map.put(CommonParameterNames.FOOTPRINT, new DataSourceParameter(CommonParameterNames.FOOTPRINT, "geo", Polygon2D.class, "Footprint"));
                map.remove("geo");
            }
        }
        return supportedParameters;
    }
}
