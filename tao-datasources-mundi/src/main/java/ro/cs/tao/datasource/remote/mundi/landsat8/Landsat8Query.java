package ro.cs.tao.datasource.remote.mundi.landsat8;

import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.converters.SimpleDateParameterConverter;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.mundi.BaseDataQuery;
import ro.cs.tao.datasource.remote.mundi.MundiDataSource;
import ro.cs.tao.datasource.remote.mundi.parsers.Landsat8ResponseHandler;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.landsat.Landsat8TileExtent;

import java.lang.reflect.Array;
import java.time.LocalDateTime;

public class Landsat8Query extends BaseDataQuery {

    static {
        ConverterFactory factory = new ConverterFactory();
        factory.register(SimpleDateParameterConverter.class, LocalDateTime.class);
        converterFactory.put(Landsat8Query.class, factory);
    }

    public Landsat8Query(MundiDataSource source, String sensorName, String connectionString) {
        super(source, sensorName, connectionString);
    }

    @Override
    protected Landsat8ResponseHandler responseHandler(String countElement) {
        return new Landsat8ResponseHandler(countElement);
    }

    @Override
    public String defaultId() { return "Mundi DIAS Landsat-8 Query"; }

    @Override
    protected String[] getFootprintsFromTileParameter() {
        String[] footprints = null;
        QueryParameter<?> tileParameter = this.parameters.get(CommonParameterNames.TILE);
        Object value = tileParameter.getValue();
        if (value != null) {
            if (value.getClass().isArray()) {
                footprints = new String[Array.getLength(value)];
                for (int i = 0; i < footprints.length; i++) {
                    Polygon2D polygon = Polygon2D.fromPath2D(Landsat8TileExtent.getInstance().getTileExtent(Array.get(value, i).toString()));
                    footprints[i] = polygon.toWKT();
                }
            } else {
                String strVal = tileParameter.getValueAsString();
                if (strVal.startsWith("[") && strVal.endsWith("]")) {
                    String[] values = strVal.substring(1, strVal.length() - 1).split(",");
                    footprints = new String[values.length];
                    for (int i = 0; i < values.length; i++) {
                        Polygon2D polygon = Polygon2D.fromPath2D(Landsat8TileExtent.getInstance().getTileExtent(strVal));
                        footprints[i] = polygon.toWKT();
                    }
                } else {
                    Polygon2D polygon = Polygon2D.fromPath2D(Landsat8TileExtent.getInstance().getTileExtent(strVal));
                    if (polygon != null) {
                        footprints = new String[]{polygon.toWKT()};
                    }
                }
            }
        }
        return footprints;
    }
}
