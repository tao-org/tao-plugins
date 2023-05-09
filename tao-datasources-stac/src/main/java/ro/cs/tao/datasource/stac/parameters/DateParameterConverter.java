package ro.cs.tao.datasource.stac.parameters;

import ro.cs.tao.datasource.converters.DefaultParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 2022-05-01T00:00:00Z
public class DateParameterConverter extends DefaultParameterConverter<LocalDateTime> {
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private final DateTimeFormatter dateFormat;

    public DateParameterConverter(QueryParameter<LocalDateTime> parameter) {
        super(parameter);
        if (!LocalDateTime.class.equals(parameter.getType())) {
            throw new IllegalArgumentException("Invalid parameter type");
        }
        dateFormat = DateTimeFormatter.ofPattern(DATE_FORMAT);
    }

    @Override
    public String stringValue() {
        return parameter.getValue().format(dateFormat) + "Z";
    }
}