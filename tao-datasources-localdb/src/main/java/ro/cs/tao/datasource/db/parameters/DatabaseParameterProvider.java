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
package ro.cs.tao.datasource.db.parameters;

import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.ProductFetchStrategy;
import ro.cs.tao.datasource.db.DatabaseSource;
import ro.cs.tao.datasource.db.fetch.DatabaseFetchStrategy;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.datasource.param.ParameterProvider;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;

import java.util.*;

/**
 * Parameter provider implementation for local database.
 *
 * @author Cosmin Cara
 */
public class DatabaseParameterProvider implements ParameterProvider {

    private final DatabaseSource source;

    public DatabaseParameterProvider(DatabaseSource source) {
        this.source = source;
    }

    @Override
    public Map<String, Map<ParameterName, DataSourceParameter>> getSupportedParameters() {
        String[] sensors = getSupportedSensors();
        return Collections.unmodifiableMap(
                new HashMap<String, Map<ParameterName, DataSourceParameter>>() {{
                    for (String sensor : sensors) {
                        put(sensor, new LinkedHashMap<ParameterName, DataSourceParameter>() {{
                            put(ParameterName.create(CommonParameterNames.PRODUCT, "name", "Product Name"),
                                new DataSourceParameter("name", String[].class, false));
                            put(ParameterName.create("productFormat", "type_id", "Product Format"),
                                new DataSourceParameter("type_id", DataFormat.class, false));
                            put(ParameterName.create(CommonParameterNames.FOOTPRINT, "geometry", "Region of Interest"),
                                new DataSourceParameter("geometry", Polygon2D.class, false));
                            put(ParameterName.create("crs", "coordinate_reference_system", "CRS"),
                                new DataSourceParameter("coordinate_reference_system", String.class, false));
                            put(ParameterName.create("sensorType", "sensor_type_id", "Sensor Type"),
                                new DataSourceParameter("sensor_type_id", SensorType.class, false));
                            put(ParameterName.create(CommonParameterNames.START_DATE, "acquisition_date", "Acquisition Date"),
                                new DataSourceParameter("acquisition_date", Date.class, false));
                            put(ParameterName.create(CommonParameterNames.PRODUCT_TYPE, "product_type", "Satellite"),
                                new DataSourceParameter("product_type", String.class, sensor));
                        }});
                    }
                }});
    }

    @Override
    public String[] getSupportedSensors() {
        Set<String> sensors = new HashSet<>();
        ServiceRegistry<DataSource> serviceRegistry = ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
        Set<DataSource> services = serviceRegistry.getServices();
        for (DataSource service : services) {
            if (!service.getClass().equals(this.source.getClass())) {
                Collections.addAll(sensors, service.getSupportedSensors());
            }
        }
        /*List<String> sensors = new ArrayList<>();
        Connection sqlConnection = this.source.getConnection();
        if (sqlConnection != null) {
            try {
                PreparedStatement statement = sqlConnection.prepareStatement("SELECT DISTINCT product_type FROM " + DatabaseSource.PRODUCTS_TABLE);
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    sensors.add(resultSet.getString(0));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    sqlConnection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }*/
        return sensors.toArray(new String[0]);
    }

    @Override
    public Map<String, ProductFetchStrategy> getRegisteredProductFetchStrategies() {
        String[] sensors = getSupportedSensors();
        return Collections.unmodifiableMap(
                new HashMap<String, ProductFetchStrategy>() {{
                    for (String sensor : sensors) {
                        put(sensor, new DatabaseFetchStrategy(DatabaseParameterProvider.this.source));
                    }
                }});
    }
}
