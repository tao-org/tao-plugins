package ro.cs.tao.datasource.remote.fedeo.parameters;

import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.DefaultParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.eodata.Polygon2D;

public class FedEOPolygon2DConverter extends DefaultParameterConverter<Polygon2D> {
    public FedEOPolygon2DConverter(QueryParameter<Polygon2D> parameter) {
        super(parameter);
        if (!Polygon2D.class.equals(parameter.getType())) {
            throw new IllegalArgumentException("Invalid parameter type");
        }
    }

    @Override
    public String stringValue() throws ConversionException {
        return super.parameter.getValue().toWKTCounterClockwise();
    }
}
