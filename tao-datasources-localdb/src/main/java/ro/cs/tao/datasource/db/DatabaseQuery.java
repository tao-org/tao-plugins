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
package ro.cs.tao.datasource.db;

import ro.cs.tao.EnumUtils;
import ro.cs.tao.TaoEnum;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.converters.DateParameterConverter;
import ro.cs.tao.datasource.converters.PolygonParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;

import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Database query implementation.
 *
 * @author Cosmin Cara
 */
public class DatabaseQuery extends DataQuery {

    private Logger logger;

    static {
        ConverterFactory factory = new ConverterFactory();
        factory.register(PolygonParameterConverter.class, Polygon2D.class);
        factory.register(DateParameterConverter.class, LocalDateTime.class);
        converterFactory.put(DatabaseQuery.class, factory);
    }
    DatabaseQuery(DatabaseSource source, String sensorName) {
        super(source, sensorName);
        this.logger = Logger.getLogger(DatabaseQuery.class.getName());
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        List<EOProduct> results = new ArrayList<>();
        Connection sqlConnection = ((DatabaseSource) this.source).getConnection();
        if (sqlConnection != null) {
            try {
                StringBuilder query = new StringBuilder("SELECT id, name, type_id, geometry, coordinate_reference_system, " +
                        "location, entry_point, sensor_type_id, acquisition_date, pixel_type_id, product_type, width, height, " +
                        "approximate_size FROM " + DatabaseSource.PRODUCTS_TABLE + " WHERE ");
                int idx = 1;
                List<ParameterIndex> values = new ArrayList<>();
                for (Map.Entry<String, QueryParameter<?>> entry : this.parameters.entrySet()) {
                    QueryParameter<?> parameter = entry.getValue();
                    if (!parameter.isOptional() && !parameter.isInterval() && parameter.getValue() == null) {
                        throw new QueryException(String.format("Parameter [%s] is required but no value is supplied", parameter.getName()));
                    }
                    if (parameter.isOptional() &
                            ((!parameter.isInterval() & parameter.getValue() == null) |
                                    (parameter.isInterval() & parameter.getMinValue() == null & parameter.getMaxValue() == null))) {
                        continue;
                    }
                    if (idx > 1) {
                        query.append(" AND ");
                    }
                    if (parameter.getType().isArray()) {
                        query.append(getRemoteName(parameter.getName())).append(" IN (");
                        Object value = parameter.getValue();
                        if (!value.getClass().isArray()) {
                            // the value should be a string on the form "[a,b,..]"
                            String strValue = (String) value;
                            value = strValue.substring(1, strValue.length() - 1).split(",");
                        }
                        Object[] arrayValue;
                        int length;
                        try {
                            length = Array.getLength(value);
                            arrayValue = new Object[length];
                            for (int i = 0; i < length; i++) {
                                query.append("?");
                                if (i < length - 1) {
                                    query.append(",");
                                }
                                arrayValue[i] = Array.get(value, i);
                            }
                        } catch (Exception e) {
                            length = 1;
                            arrayValue = new Object[length];
                            arrayValue[0] = value;
                            query.append("?");
                        }
                        query.append(") ");
                        values.add(new ParameterIndex(idx, idx + length - 1, parameter.getType(), arrayValue));
                        idx += length;
                    } else {
                        if (parameter.isInterval()) {
                            query.append(getRemoteName(parameter.getName())).append(" BETWEEN ? AND ?");
                            values.add(new ParameterIndex(idx, idx + 1, parameter.getType(),
                                                          parameter.getMinValue(),
                                                          parameter.getMaxValue()));
                            idx += 2;
                        } else {
                            if (!Polygon2D.class.equals(parameter.getType())) {
                                query.append(getRemoteName(parameter.getName())).append("=? ");
                            } else {
                                query.append(" st_intersects(").append(getRemoteName(parameter.getName())).append(", st_geomfromtext(?)) ");
                            }
                            values.add(new ParameterIndex(idx, idx, parameter.getType(), parameter.getValue()));
                            idx += 1;
                        }
                    }
                }
                if (this.pageNumber > 0 && this.pageSize > 0) {
                    query.append(" OFFSET ").append((this.pageNumber - 1) * this.pageSize);
                }
                if (this.limit > 0) {
                    query.append(" LIMIT ").append(this.limit);
                }
                final PreparedStatement statement = sqlConnection.prepareStatement(query.toString());
                for (ParameterIndex paramIndex : values) {
                    for (int i = paramIndex.fromIndex; i <= paramIndex.toIndex; i++) {
                        Object value = paramIndex.values[i - paramIndex.fromIndex];
                        if (value == null) {
                             statement.setNull(i, Types.VARCHAR);
                        } else {
                            Class clazz = value.getClass();
                            if (paramIndex.paramClass.isEnum()) {
                                // Enum values come as String, therefore we need to extract the integer value
                                statement.setObject(i, ((TaoEnum) Enum.valueOf(paramIndex.paramClass, value.toString())).value(), Types.INTEGER);
                            } else if (Byte.class.equals(clazz)) {
                                statement.setObject(i, value, Types.TINYINT);
                            } else if (Short.class.equals(clazz)) {
                                statement.setObject(i, value, Types.SMALLINT);
                            } else if (Integer.class.equals(clazz)) {
                                statement.setObject(i, value, Types.INTEGER);
                            } else if (Long.class.equals(clazz)) {
                                statement.setObject(i, value, Types.BIGINT);
                            } else if (Float.class.equals(clazz)) {
                                statement.setObject(i, value, Types.FLOAT);
                            } else if (Double.class.equals(clazz)) {
                                statement.setObject(i, value, Types.DOUBLE);
                            } else if (String.class.equals(clazz)) {
                                statement.setString(i, (String) value);
                            } else if (Polygon2D.class.equals(clazz)) {
                                statement.setString(i, ((Polygon2D) value).toWKT());
                            } else if (LocalDateTime.class.equals(clazz)) {
                                statement.setObject(i, Timestamp.valueOf((LocalDateTime) value), Types.TIMESTAMP);
                            }
                        }
                    }
                }
                logger.fine(String.format("Executing query: %s", statement.toString()));
                final ResultSet resultSet = statement.executeQuery();
                // TODO Fix field order
                while (resultSet.next()) {
                    EOProduct product = new EOProduct();
                    product.setId(resultSet.getString(1));
                    product.setName(resultSet.getString(2));
                    int typeId = resultSet.getInt(3);
                    if (typeId != 0) {
                        product.setFormatType(EnumUtils.getEnumConstantByValue(DataFormat.class, typeId));
                    }
                    String geometry = resultSet.getString(4);
                    if (geometry != null) {
                        try {
                            product.setGeometry(geometry);
                        } catch (Exception e) {
                            logger.warning(e.getMessage());
                        }
                    }
                    String crs = resultSet.getString(5);
                    if (crs != null) {
                        product.setCrs(crs);
                    }
                    try {
                        product.setLocation(resultSet.getString(6));
                    } catch (URISyntaxException e) {
                        logger.warning(e.getMessage());
                    }
                    product.setEntryPoint(resultSet.getString(7));
                    int sensorTypeId = resultSet.getInt(8);
                    if (sensorTypeId != 0) {
                        product.setSensorType(EnumUtils.getEnumConstantByValue(SensorType.class, sensorTypeId));
                    }
                    final Timestamp timestamp = resultSet.getTimestamp(9);
                    if (timestamp != null) {
                        product.setAcquisitionDate(timestamp.toLocalDateTime());
                    }
                    int pixelTypeId = resultSet.getInt(10);
                    if (pixelTypeId != 0) {
                        product.setPixelType(EnumUtils.getEnumConstantByValue(PixelType.class, pixelTypeId));
                    }
                    product.setProductType(resultSet.getString(11));
                    product.setWidth(resultSet.getInt(12));
                    product.setHeight(resultSet.getInt(13));
                    product.setApproximateSize(resultSet.getLong(14));
                    results.add(product);
                }
            } catch (SQLException ex) {
                logger.severe(ex.getMessage());
            }
        }
        return results;
    }

    @Override
    public String defaultId() {
        return "DatabaseQuery";
    }

    private class ParameterIndex {
        Class paramClass;
        Object[] values;
        int fromIndex;
        int toIndex;

        ParameterIndex(int fromIndex, int toIndex, Class paramClass, Object... values) {
            this.paramClass = paramClass;
            this.values = values;
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
        }
    }
}
