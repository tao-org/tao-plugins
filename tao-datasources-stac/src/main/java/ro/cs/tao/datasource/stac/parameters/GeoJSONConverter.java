package ro.cs.tao.datasource.stac.parameters;

import org.geotools.geojson.geom.GeometryJSON;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.DefaultParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.serialization.GeometryAdapter;

public class GeoJSONConverter extends DefaultParameterConverter<Polygon2D> {

    public GeoJSONConverter(QueryParameter<Polygon2D> parameter) {
        super(parameter);
    }

    @Override
    public String stringValue() throws ConversionException {
        Polygon2D value = this.parameter.getValue();
        GeometryJSON gJson = new GeometryJSON();
        String output = "";
        try {
            return gJson.toString(new GeometryAdapter().marshal(value.toWKT()));
        } catch (Exception e) {
            throw new ConversionException(e.getMessage());
        }
    }
}
