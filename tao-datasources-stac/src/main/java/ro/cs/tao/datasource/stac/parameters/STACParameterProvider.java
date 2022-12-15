package ro.cs.tao.datasource.stac.parameters;

import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.stac.STACSource;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.stac.core.STACClient;
import ro.cs.tao.stac.core.model.Collection;
import ro.cs.tao.stac.core.model.CollectionList;
import ro.cs.tao.stac.core.model.Extent;
import ro.cs.tao.utils.Tuple;

import java.io.IOException;
import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

public class STACParameterProvider implements ParameterProvider {
    private static final String DS_TABLE = "component.data_source_component";
    private static Map<String, CollectionDescription> sensorTypes;
    private final STACSource source;

    public STACParameterProvider(STACSource source) {
        this.source = source;
    }

    @Override
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        String[] sensors = this.source.getSupportedSensors(); //getSupportedSensors();
        return Collections.unmodifiableMap(
                new HashMap<>() {{
                    for (String sensor : sensors) {
                        put(sensor, new LinkedHashMap<>() {{
                            Tuple<String, DataSourceParameter> parameter =
                                    ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "bbox", "Region of Interest", Polygon2D.class);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "datetime", "Begin Date", LocalDateTime.class);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                            parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "datetime", "End Date", LocalDateTime.class);
                            put(parameter.getKeyOne(), parameter.getKeyTwo());
                        }});
                    }
                }});
    }

    @Override
    public String[] getSupportedSensors() {
        List<String> collections = new ArrayList<>();
        try (Connection sqlConnection = this.source.getConnection();
             PreparedStatement statement = sqlConnection.prepareStatement("SELECT DISTINCT sensor_name FROM " + DS_TABLE + " WHERE data_source_name = ?")) {
            statement.setString(1, this.source.getConnectionString());
            final ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                collections.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return collections.toArray(new String[0]);
    }

    @Override
    public Map<String, CollectionDescription> getSensorTypes() {
        if (sensorTypes == null) {
            sensorTypes = new LinkedHashMap<>();
            try {
                final STACClient client = this.source.authenticate();
                CollectionList collectionList = client.listCollections();
                List<Collection> collections = collectionList.getCollections();
                if (collections != null) {
                    collections.sort(Comparator.comparing(Collection::getTitle));
                    String mission;
                    Map<String, Object> summaries;
                    for (Collection collection : collections) {
                        summaries = collection.getSummaries();
                        mission = getObjectValue(summaries.get("mission"));
                        if (mission == null) {
                            mission = getObjectValue(summaries.get("platform"));
                        }
                        CollectionDescription description = new CollectionDescription();
                        description.setMission(mission);
                        description.setDescription(collection.getDescription());
                        description.setSensorType(SensorType.UNKNOWN);
                        Extent extent = collection.getExtent();
                        if (extent != null && extent.getSpatial() != null) {
                            description.setSpatialCoverage(Arrays.toString(extent.getSpatial().getBbox()));
                        }
                        if (extent != null && extent.getTemporal() != null) {
                            LocalDateTime[] interval = extent.getTemporal().getInterval();
                            description.setTemporalCoverage("From " + interval[0].toLocalDate().format(DateTimeFormatter.ofPattern("MMM yyyy"))
                                                                    + " to " + (interval[1] != null
                                                                                ? interval[0].toLocalDate().format(DateTimeFormatter.ofPattern("MMM yyyy"))
                                                                                : "present"));
                        }
                        sensorTypes.put(collection.getId(), description);
                    }
                }
            } catch (IOException e) {
                Logger.getLogger(STACParameterProvider.class.getName()).severe(e.getMessage());
            }
        }
        return sensorTypes;
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() {
        return null;
    }

    private String getObjectValue(Object object) {
        if (object == null) {
            return null;
        }
        if (object.getClass().isArray()) {
            return String.valueOf(Array.get(object, 0));
        } else {
            return String.valueOf(object);
        }
    }
}
