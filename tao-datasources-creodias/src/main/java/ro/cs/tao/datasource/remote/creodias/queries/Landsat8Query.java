package ro.cs.tao.datasource.remote.creodias.queries;

import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.remote.creodias.BaseDataQuery;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.DateParameterConverter;
import ro.cs.tao.datasource.remote.creodias.parsers.Landsat8JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

import java.time.LocalDateTime;


public class Landsat8Query extends BaseDataQuery {

    static {
        final ConverterFactory factory = new ConverterFactory();
        factory.register(DateParameterConverter.class, LocalDateTime.class);
        converterFactory.put(Landsat8Query.class, factory);
    }

    public Landsat8Query(CreoDiasDataSource source, String sensorName, String connectionString) {
        super(source, sensorName, connectionString);
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Landsat8JsonResponseHandler();
    }

    @Override
    public String defaultId() { return "Creo DIAS Landsat-8 Query"; }

}
