package ro.cs.tao.datasource.remote.creodias.sentinel2;

import ro.cs.tao.datasource.remote.creodias.BaseDataQuery;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.Sentinel2JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

public class Sentinel2Query extends BaseDataQuery {

    public Sentinel2Query(CreoDiasDataSource source, String sensorName, String connectionString) {
        super(source, sensorName, connectionString);
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Sentinel2JsonResponseHandler();
    }

    @Override
    public String defaultId() { return "Creo DIAS Sentinel-2 Query"; }

}
