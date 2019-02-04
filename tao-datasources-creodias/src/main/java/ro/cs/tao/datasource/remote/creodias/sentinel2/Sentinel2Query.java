package ro.cs.tao.datasource.remote.creodias.sentinel2;

import ro.cs.tao.datasource.remote.creodias.BaseDataQuery;
import ro.cs.tao.datasource.remote.creodias.BaseDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.Sentinel2JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

public class Sentinel2Query extends BaseDataQuery {

    public Sentinel2Query(BaseDataSource source) {
        super(source, "Sentinel2");
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Sentinel2JsonResponseHandler();
    }

    @Override
    public String defaultId() { return "CreoDIAS Sentinel-2 Query"; }

}
