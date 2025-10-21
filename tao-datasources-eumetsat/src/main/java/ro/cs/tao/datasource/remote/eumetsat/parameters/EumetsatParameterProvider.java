package ro.cs.tao.datasource.remote.eumetsat.parameters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;
import ro.cs.tao.component.SystemVariable;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.opensearch.OpenSearchParameterProvider;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.remote.eumetsat.EumetsatDataSource;
import ro.cs.tao.datasource.remote.eumetsat.download.EumetsatDownloadStrategy;
import ro.cs.tao.datasource.remote.eumetsat.model.Root;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.utils.NetUtils;
import ro.cs.tao.utils.StringUtilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

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
        final Map<String, CollectionDescription> descriptions = new LinkedHashMap<>();
        String[] sensors = getSupportedSensors();
        CollectionDescription current;
        for (String sensor : sensors) {
            current = readDescription(sensor);
            if (current == null) {
                current = readRemoteDescription(sensor);
                if (current != null) {
                    final Path path = Paths.get(SystemVariable.ROOT.value()).resolve("collections").resolve(sensor.replace(":", "-") + ".json");
                    try {
                        Files.createDirectories(path.getParent());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try (OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
                        new ObjectMapper().writerFor(CollectionDescription.class).writeValue(stream, current);
                        System.out.println("Written " + path.toString());
                    } catch (IOException e) {
                        Logger.getLogger(EumetsatParameterProvider.class.getName()).warning(e.getMessage());
                    }
                }
            }
            if (current != null) {
                descriptions.put(sensor, current);
            }
        }
        return descriptions;
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

    private CollectionDescription readDescription(String collectionName) {
        final Path path = Paths.get(SystemVariable.ROOT.value()).resolve("collections").resolve(collectionName.replace(":", "-") + ".json");
        if (Files.exists(path)) {
            try (InputStream stream = Files.newInputStream(path)) {
                return new ObjectMapper().readerFor(CollectionDescription.class).readValue(stream);
            } catch (IOException e) {
                Logger.getLogger(EumetsatParameterProvider.class.getName()).warning(e.getMessage());
            }
        }
        return null;
    }

    private CollectionDescription readRemoteDescription(String collectionName) {
        CollectionDescription description = null;
        try {
            String response = NetUtils.getResponseAsString(EumetsatDataSource.getCollectionUrl() + collectionName);
            if (!StringUtilities.isNullOrEmpty(response) && !response.contains("error")) {
                final ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                final Root root = mapper.readValue(response, Root.class);
                description = new CollectionDescription();
                description.setDescription(root._source.myabstract);
                description.setMission(root._source.satellites.get(0).seriesName);
                description.setSensorType(description.getMission().equals("SENTINEL-6")
                                                  ? SensorType.ALTIMETRIC
                                                  : SensorType.ATMOSPHERIC);
                description.setSpatialCoverage(root._source.location.toString());
                description.setTemporalCoverage(root._source.temporalExtent.toString());
                description.setTokenNeeded(true);
            } else {
                String[] newSensors = new String[this.sensors.length - 1];
                for (int i = 0; i < this.sensors.length - 1; i++) {
                    if (this.sensors[i].equals(collectionName)) {
                        for (int j = i + 1; j < this.sensors.length; j++) {
                            newSensors[j - 1] = this.sensors[j];
                        }
                        break;
                    } else {
                        newSensors[i] = this.sensors[i];
                    }
                }
                this.sensors = newSensors;
            }
        } catch (IOException e) {
            Logger.getLogger(EumetsatParameterProvider.class.getName()).warning(e.getMessage());
        }
        return description;
    }
}
