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
package ro.cs.tao.datasource.remote.fedeo.parameters;

import ro.cs.tao.datasource.converters.DefaultParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Cosmin Cara
 */
public class FedEODateParameterConverter extends DefaultParameterConverter<LocalDateTime> {
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.S'Z'";
    private final DateTimeFormatter dateFormat;

    public FedEODateParameterConverter(QueryParameter<LocalDateTime> parameter) {
        super(parameter);
        if (!LocalDateTime.class.equals(parameter.getType())) {
            throw new IllegalArgumentException("Invalid parameter type");
        }
        dateFormat = DateTimeFormatter.ofPattern(DATE_FORMAT);
    }

    @Override
    public String stringValue() {
        return parameter.getValue().format(dateFormat);
    }
}