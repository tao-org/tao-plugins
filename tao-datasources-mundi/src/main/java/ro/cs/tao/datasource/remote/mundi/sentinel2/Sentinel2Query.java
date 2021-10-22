package ro.cs.tao.datasource.remote.mundi.sentinel2;

import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.converters.SimpleDateParameterConverter;
import ro.cs.tao.datasource.remote.mundi.BaseDataQuery;
import ro.cs.tao.datasource.remote.mundi.MundiDataSource;
import ro.cs.tao.datasource.remote.mundi.parsers.Sentinel2ResponseHandler;

import java.time.LocalDateTime;

public class Sentinel2Query extends BaseDataQuery {

    static {
        ConverterFactory factory = new ConverterFactory();
        factory.register(SimpleDateParameterConverter.class, LocalDateTime.class);
        converterFactory.put(Sentinel2Query.class, factory);
    }

    public Sentinel2Query(MundiDataSource source, String sensorName, String connectionString) {
        super(source, sensorName, connectionString);
    }

    @Override
    protected Sentinel2ResponseHandler responseHandler(String countElement) {
        return new Sentinel2ResponseHandler(countElement);
    }

    @Override
    public String defaultId() { return "Mundi DIAS Sentinel-2 Query"; }

}
