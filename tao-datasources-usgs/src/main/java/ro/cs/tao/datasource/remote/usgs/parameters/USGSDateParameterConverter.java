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
        if (parameter.getName() == "sensingStart") {
            return parameter.getMinValueAsFormattedDate(DATE_FORMAT);
        } else if (parameter.getName() == "sensingEnd") {
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
