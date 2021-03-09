package ro.cs.tao.datasource.remote.earthdata.parameters;

import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.DefaultParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateParameterConverter extends DefaultParameterConverter {

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    private final DateTimeFormatter dateFormat;

    public DateParameterConverter(QueryParameter<?> parameter){
        super(parameter);
        if (!Date.class.equals(parameter.getType())) {
            throw new IllegalArgumentException("Invalid parameter type");
        }
        dateFormat = DateTimeFormatter.ofPattern(DATE_FORMAT);
    }

    @Override
    public String stringValue() throws ConversionException {
        StringBuilder builder = new StringBuilder();
        if (parameter.getMinValue() != null) {
            LocalDateTime start = ((Date) parameter.getMinValue()).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            builder.append(start.format(dateFormat)).append("Z,");
        }
        if (parameter.getMinValue() == null && parameter.getMaxValue() != null) {
            builder.append(",");
        }
        if (parameter.getMaxValue() != null){
            LocalDateTime end = ((Date) parameter.getMaxValue()).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            builder.append(end.format(dateFormat)).append("Z");
        }
        return builder.toString();
    }


}
