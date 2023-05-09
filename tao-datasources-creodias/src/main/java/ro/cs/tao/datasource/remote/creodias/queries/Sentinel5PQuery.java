package ro.cs.tao.datasource.remote.creodias.queries;

import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.remote.creodias.BaseDataQuery;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.DateParameterConverter;
import ro.cs.tao.datasource.remote.creodias.parsers.Sentinel5PJsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

import java.time.LocalDateTime;

public class Sentinel5PQuery extends BaseDataQuery {

    static {
        final ConverterFactory factory = new ConverterFactory();
        factory.register(DateParameterConverter.class, LocalDateTime.class);
        converterFactory.put(Sentinel5PQuery.class, factory);
    }

    public Sentinel5PQuery(CreoDiasDataSource source, String sensorName, String connectionString) {
        super(source, sensorName, connectionString);
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Sentinel5PJsonResponseHandler();
    }

    @Override
    public String defaultId() { return "Creo DIAS Sentinel-5P Query"; }
}
