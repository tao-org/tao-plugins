package ro.cs.tao.datasource.remote.mundi.sentinel1;

import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.converters.SimpleDateParameterConverter;
import ro.cs.tao.datasource.remote.mundi.BaseDataQuery;
import ro.cs.tao.datasource.remote.mundi.MundiDataSource;
import ro.cs.tao.datasource.remote.mundi.parsers.Sentinel1ResponseHandler;

import java.time.LocalDateTime;

public class Sentinel1Query extends BaseDataQuery {

    static {
        ConverterFactory factory = new ConverterFactory();
        factory.register(SimpleDateParameterConverter.class, LocalDateTime.class);
        converterFactory.put(Sentinel1Query.class, factory);
    }

    public Sentinel1Query(MundiDataSource source, String sensorName, String connectionString) {
        super(source, sensorName, connectionString);
    }

    @Override
    protected Sentinel1ResponseHandler responseHandler(String countElement) {
        return new Sentinel1ResponseHandler(countElement);
    }

    @Override
    public String defaultId() { return "Mundi DIAS Sentinel-1 Query"; }

}
