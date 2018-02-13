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
            Double value = (Double) this.parameter.getValue();
            maxValue = value != null ? value : 100.;
        }
        builder.append(maxValue).append("]");
        return builder.toString();
    }
}
