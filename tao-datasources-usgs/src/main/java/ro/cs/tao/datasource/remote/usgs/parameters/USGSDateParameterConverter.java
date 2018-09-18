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
package ro.cs.tao.datasource.remote.usgs.parameters;

import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.DateParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * @author Cosmin Cara
 */
public class USGSDateParameterConverter extends DateParameterConverter {
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    public USGSDateParameterConverter(QueryParameter parameter) {
        super(parameter);
        this.dateFormat = DateTimeFormatter.ofPattern(DATE_FORMAT);
    }

    @Override
    public String stringValue() throws ConversionException {
        if (parameter.isInterval() && "sensingStart".equals(parameter.getName())) {
            return parameter.getMinValueAsFormattedDate(DATE_FORMAT);
        } else if (parameter.isInterval() && "sensingEnd".equals(parameter.getName())) {
            return parameter.getMaxValueAsFormattedDate(DATE_FORMAT);
        }
        Date minValue = (Date) parameter.getValue();
        if (minValue != null) {
            LocalDateTime minDate = minValue.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return minDate.format(dateFormat);
        } else {
            return null;
        }
    }
}
