package ro.cs.tao.datasource.stac.parameters;

import ro.cs.tao.Tuple;
import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.datasource.stac.STACSource;
import ro.cs.tao.eodata.Polygon2D;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

public class STACParameterProvider implements ParameterProvider {
    private static final String DS_TABLE = "component.data_source_component";
    private final STACSource source;

    public STACParameterProvider(STACSource source) {
        this.source = source;
    }

    @Override
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        String[] sensors = getSupportedSensors();
        return Collections.unmodifiableMap(
                new HashMap<String, Map<String, DataSourceParameter>>() {{
                    for (String sensor : sensors) {
                        put(sensor, new LinkedHashMap<String, DataSourceParameter>() {{
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
             PreparedStatement statement = sqlConnection.prepareStatement("SELECT DISTINCT sensor_name FROM " + DS_TABLE + " WHERE data_source_name = 'STAC Service'")) {
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
        return null;
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() {
        return null;
    }
}
