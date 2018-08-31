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
package ro.cs.tao.datasource.remote.scihub.parameters;

import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.DefaultParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;

/**
 * @author Cosmin Cara
 */
public class DoubleParameterConverter extends DefaultParameterConverter {
    public DoubleParameterConverter(QueryParameter parameter) {
        super(parameter);
        if (!Double.class.equals(parameter.getType())) {
            throw new IllegalArgumentException("Incorrect parameter type");
        }
    }

    @Override
    public String stringValue() throws ConversionException {
        StringBuilder builder = new StringBuilder();
        Double minValue = (Double) this.parameter.getMinValue();
        if (minValue == null) {
            minValue = 0.0;
        }
        builder.append("[").append(minValue).append(" TO ");
        Double maxValue = (Double) this.parameter.getMaxValue();
        if (maxValue == null) {
            Object objVal = this.parameter.getValue();
            Double value = null;
            if (objVal != null) {
                if (objVal instanceof Double) {
                    value = (Double) objVal;
                } else {
                    value = Double.parseDouble(objVal.toString());
                }
            }
            maxValue = value != null ? value : 100.;
        }
        builder.append(maxValue).append("]");
        return builder.toString();
    }
}
