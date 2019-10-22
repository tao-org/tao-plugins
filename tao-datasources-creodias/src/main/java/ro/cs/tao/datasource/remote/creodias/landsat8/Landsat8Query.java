package ro.cs.tao.datasource.remote.creodias.landsat8;

import ro.cs.tao.datasource.remote.creodias.BaseDataQuery;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.Landsat8JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

public class Landsat8Query extends BaseDataQuery {

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
