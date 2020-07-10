package ro.cs.tao.datasource.remote.creodias.sentinel1;

import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.remote.creodias.BaseDataQuery;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.DateParameterConverter;
import ro.cs.tao.datasource.remote.creodias.parsers.Sentinel1JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

import java.util.Date;

public class Sentinel1Query extends BaseDataQuery {

    static {
        final ConverterFactory factory = new ConverterFactory();
        factory.register(DateParameterConverter.class, Date.class);
        converterFactory.put(Sentinel1Query.class, factory);
    }

    public Sentinel1Query(CreoDiasDataSource source, String sensorName, String connectionString) {
        super(source, sensorName, connectionString);
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Sentinel1JsonResponseHandler();
    }

    @Override
    public String defaultId() { return "Creo DIAS Sentinel-1 Query"; }

}
