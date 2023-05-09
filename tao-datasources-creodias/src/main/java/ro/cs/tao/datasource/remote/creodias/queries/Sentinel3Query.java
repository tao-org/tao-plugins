package ro.cs.tao.datasource.remote.creodias.queries;

import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.remote.creodias.BaseDataQuery;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.DateParameterConverter;
import ro.cs.tao.datasource.remote.creodias.parsers.Sentinel3JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

import java.time.LocalDateTime;

public class Sentinel3Query extends BaseDataQuery {

    static {
        final ConverterFactory factory = new ConverterFactory();
        factory.register(DateParameterConverter.class, LocalDateTime.class);
        converterFactory.put(Sentinel3Query.class, factory);
    }

    public Sentinel3Query(CreoDiasDataSource source, String sensorName, String connectionString) {
        super(source, sensorName, connectionString);
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Sentinel3JsonResponseHandler();
    }

    @Override
    public String defaultId() { return "Creo DIAS Sentinel-3 Query"; }

}
