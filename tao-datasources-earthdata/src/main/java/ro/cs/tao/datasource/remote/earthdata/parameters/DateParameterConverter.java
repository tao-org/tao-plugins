package ro.cs.tao.datasource.remote.earthdata.parameters;

import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.DefaultParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateParameterConverter extends DefaultParameterConverter<LocalDateTime> {

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    private final DateTimeFormatter dateFormat;

    public DateParameterConverter(QueryParameter<LocalDateTime> parameter){
        super(parameter);
        if (!LocalDateTime.class.equals(parameter.getType())) {
            throw new IllegalArgumentException("Invalid parameter type");
        }
        dateFormat = DateTimeFormatter.ofPattern(DATE_FORMAT);
    }

    @Override
    public String stringValue() throws ConversionException {
        StringBuilder builder = new StringBuilder();
        if (parameter.getMinValue() != null) {
            builder.append(parameter.getMinValue().format(dateFormat)).append("Z,");
        }
        if (parameter.getMaxValue() != null) {
            builder.append(",");
        }
        if (parameter.getMaxValue() != null){
            builder.append(parameter.getMaxValue().format(dateFormat)).append("Z");
        }
        return builder.toString();
    }


}
