package ro.cs.tao.datasource.remote.creodias.landsat8;

import ro.cs.tao.datasource.remote.creodias.BaseDataQuery;
import ro.cs.tao.datasource.remote.creodias.BaseDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.Landsat8JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

public class Landsat8Query extends BaseDataQuery {

    public Landsat8Query(BaseDataSource source) {
        super(source, "Landsat8");
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Landsat8JsonResponseHandler();
    }

    @Override
    public String defaultId() { return "CreoDIAS Landsat-8 Query"; }

}
