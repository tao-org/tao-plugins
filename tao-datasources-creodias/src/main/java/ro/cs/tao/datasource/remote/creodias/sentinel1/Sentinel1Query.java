package ro.cs.tao.datasource.remote.creodias.sentinel1;

import ro.cs.tao.datasource.remote.creodias.BaseDataQuery;
import ro.cs.tao.datasource.remote.creodias.BaseDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.Sentinel1JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

public class Sentinel1Query extends BaseDataQuery {

    public Sentinel1Query(BaseDataSource source) {
        super(source, "Sentinel1");
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Sentinel1JsonResponseHandler();
    }

    @Override
    public String defaultId() { return "CreoDIAS Sentinel-1 Query"; }

}
