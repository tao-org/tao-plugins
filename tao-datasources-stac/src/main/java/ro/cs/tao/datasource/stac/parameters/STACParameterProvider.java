package ro.cs.tao.datasource.stac.parameters;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.stac.STACSource;
import ro.cs.tao.datasource.stac.fetch.STACFetchStrategy;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.stac.core.STACClient;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.model.Collection;
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
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class STACParameterProvider implements ParameterProvider {
    private static Map<String, CollectionDescription> sensorTypes;
    private final STACSource source;
    private Map<String, ProductFetchStrategy> productFetchers;
    private Map<String, Map<String, DataSourceParameter>> dataSourceParameters;
    private Map<String, DataSourceParameter> commonParameters;


    public STACParameterProvider(STACSource source) {
        this.source = source;
    }

    @Override
    public Map<String, DataSourceParameter> getSupportedParameters(String sensor) {
        if (this.dataSourceParameters == null) {
            this.dataSourceParameters = new LinkedHashMap<>();
        }
        if (!this.dataSourceParameters.containsKey(sensor)) {
            try {
                final STACClient client = this.source.authenticate();
                loadCommonParameters();
                final Map<String, DataSourceParameter> sensorParams = new LinkedHashMap<>();
                sensorParams.putAll(this.commonParameters);
                sensorParams.putAll(loadSensorParameters(sensor));
                this.dataSourceParameters.put(sensor, sensorParams);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this.dataSourceParameters.get(sensor);
    }

    @Override
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        if (this.dataSourceParameters == null) {
            this.dataSourceParameters = new LinkedHashMap<>();
        }
        String[] sensors = this.source.getSupportedSensors();
        loadCommonParameters();
        for (String sensor : sensors) {
            if (!this.dataSourceParameters.containsKey(sensor)) {
                this.dataSourceParameters.put(sensor, new LinkedHashMap<>());
                this.dataSourceParameters.get(sensor).putAll(this.commonParameters);
                this.dataSourceParameters.get(sensor).putAll(loadSensorParameters(sensor));
            }
        }
        return this.dataSourceParameters;
    }

    @Override
    public String[] getSupportedSensors() {
        List<String> collections = new ArrayList<>();
        try (Connection sqlConnection = this.source.getConnection();
             PreparedStatement statement = sqlConnection.prepareStatement("SELECT DISTINCT a.path FROM component.container_applications a " +
                                                                                  "JOIN component.container c ON c.id = a.container_id WHERE c.name = ?")) {
            statement.setString(1, this.source.getId());
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
                            mission = getObjectValue(summaries.get("constellation"));
                            if (mission == null) {
                                mission = getObjectValue(summaries.get("platform"));
                            }
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
        if (productFetchers == null) {
            productFetchers = new HashMap<>();
        }
        final ConfigurationProvider configurationProvider = ConfigurationManager.getInstance();
        final String targetFolder = configurationProvider.getValue("product.location");
        String[] supportedSensors = getSupportedSensors();
        for (String sensor : supportedSensors) {
            productFetchers.putIfAbsent(sensor, new STACFetchStrategy(this.source, targetFolder, new Properties()));
        }
        return productFetchers;
    }

    private void loadCommonParameters() {
        if (this.commonParameters == null) {
            this.commonParameters = new LinkedHashMap<>();
            try {
                final STACClient client = this.source.authenticate();
                final QueryableList globalParameters = client.getGlobalParameters();
                final Map<String, Queryable> properties = globalParameters.getProperties();
                if (properties != null) {
                    for (Map.Entry<String, Queryable> entry : properties.entrySet()) {
                        DataSourceParameter parameter = toDataSourceParameter(null, entry.getKey(), entry.getValue());
                        this.commonParameters.put(parameter.getName(), parameter);
                    }
                }
            } catch (Exception e) {
                Logger.getLogger(STACParameterProvider.class.getName()).warning("Cannot retrieve common parameters:" + e.getMessage());
                Tuple<String, DataSourceParameter> parameter =
                        ParameterProvider.createParameter(CommonParameterNames.FOOTPRINT, "bbox", "Region of Interest", Polygon2D.class);
                this.commonParameters.put(parameter.getKeyOne(), parameter.getKeyTwo());
                parameter = ParameterProvider.createParameter(CommonParameterNames.START_DATE, "datetime", "Begin Date", LocalDateTime.class, null, true, null);
                this.commonParameters.put(parameter.getKeyOne(), parameter.getKeyTwo());
                parameter = ParameterProvider.createParameter(CommonParameterNames.END_DATE, "datetime", "End Date", LocalDateTime.class, null, true, null);
                this.commonParameters.put(parameter.getKeyOne(), parameter.getKeyTwo());
            }
        }
    }

    private Map<String, DataSourceParameter> loadSensorParameters(String sensor) {
        final Map<String, DataSourceParameter> sensorParams = new LinkedHashMap<>();
        try {
            final STACClient client = this.source.authenticate();
            final QueryableList parameters = client.getCollectionParameters(sensor);
            if (parameters != null && parameters.getProperties() != null) {
                parameters.getProperties().forEach((key, value) -> {
                    DataSourceParameter parameter = toDataSourceParameter(sensor, key, value);
                    sensorParams.put(parameter.getName(), parameter);
                });
            }
        } catch (Exception e) {
            Logger.getLogger(STACParameterProvider.class.getName()).warning("Cannot retrieve collection parameters:" + e.getMessage());
        }
        return sensorParams;
    }

    private String getObjectValue(Object object) {
        if (object == null) {
            return null;
        }
        if (object.getClass().isArray()) {
            int length = Array.getLength(object);
            if (length > 1) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    builder.append(Array.get(object, i)).append(",");
                }
                builder.setLength(builder.length() - 1);
                return builder.toString();
            } else {
                return String.valueOf(Array.get(object, 0));
            }
        } else if (object instanceof List<?>) {
            List<?> list = (List<?>) object;
            return list.stream().map(String::valueOf).collect(Collectors.joining(","));
        } else {
            return String.valueOf(object);
        }
    }

    private DataSourceParameter toDataSourceParameter(String sensor, String name, Queryable object) {
        String format = object.getFormat();
        Object type = object.getType();
        boolean isArray = type instanceof String[] || type instanceof ArrayList;
        String stringType = isArray
                ? type instanceof String[]
                  ? ((String[]) type)[0]
                  : ((ArrayList) type).get(0).toString()
                : type != null
                  ? type.toString()
                  : null;
        JavaType javaType = null;
        String localName = object.getTitle() != null ? object.getTitle() : name;
        if (format != null && format.startsWith("date")) {
            javaType = isArray ? JavaType.DATE_ARRAY : JavaType.DATE;
            boolean hasDateParam = sensor == null
                    ? this.commonParameters.containsKey(CommonParameterNames.START_DATE)
                    : (this.dataSourceParameters.get(sensor) != null && this.dataSourceParameters.get(sensor).containsKey(CommonParameterNames.START_DATE));
            localName = hasDateParam ? CommonParameterNames.END_DATE : CommonParameterNames.START_DATE;
        } else if (type != null && stringType.equals("number")) {
            javaType = isArray ? JavaType.DOUBLE_ARRAY : JavaType.DOUBLE;
        } else if (localName != null && localName.toLowerCase().contains("geometry")) {
            javaType = JavaType.POLYGON;
            localName = CommonParameterNames.FOOTPRINT;
            name = CommonParameterNames.INTERSECTS;
        }
        if (javaType == null) {
            javaType = type != null ? JavaType.fromFriendlyName(stringType) : JavaType.STRING;
        }
        final DataSourceParameter parameter = new DataSourceParameter(localName, name,
                                                                      javaType.value(), object.getDescription());
        String[] enumValues = object.getEnumValues();
        if (enumValues != null && enumValues.length > 0) {
            parameter.setValueSet(enumValues);
        }
        return parameter;
    }
}
