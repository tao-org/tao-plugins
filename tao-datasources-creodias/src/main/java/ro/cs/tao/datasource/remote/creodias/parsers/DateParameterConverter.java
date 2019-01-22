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
package ro.cs.tao.datasource.remote.creodias.parsers;

import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.DefaultParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Default query parameter converter for Date values.
 *
 * @author Cosmin Cara
 */
public class DateParameterConverter extends DefaultParameterConverter {
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    protected DateTimeFormatter dateFormat;
    public DateParameterConverter(QueryParameter parameter) {
        super(parameter);
        if (!Date.class.equals(parameter.getType())) {
            throw new IllegalArgumentException("Invalid parameter type");
        }
        dateFormat = DateTimeFormatter.ofPattern(DATE_FORMAT);
    }

    @Override
    public String stringValue() throws ConversionException {
        StringBuilder builder = new StringBuilder();
        Object minValue;
        if (parameter.isInterval()) {
            throw new ConversionException("Parameter represents an interval, but it is not supported by this data source");
        } else {
            minValue = parameter.getValue();
        }
        if (minValue != null) {
            LocalDateTime minDate = ((Date) minValue).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            builder.append(minDate.format(dateFormat));
        } else {
            throw new ConversionException("Parameter represents an interval, but the minimum value is absent");
        }
        return builder.toString();
    }
}
