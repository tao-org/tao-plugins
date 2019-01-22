package ro.cs.tao.datasource.remote.creodias.sentinel1;

import ro.cs.tao.datasource.remote.creodias.CreoDIASDataQuery;
import ro.cs.tao.datasource.remote.creodias.CreoDIASDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.Sentinel1JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

public class CreoDIASSentinel1Query extends CreoDIASDataQuery {

    public CreoDIASSentinel1Query(CreoDIASDataSource source) {
        super(source, "Sentinel1");
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Sentinel1JsonResponseHandler();
    }

    @Override
    public String defaultId() { return "CreoDIAS Sentinel-1 Query"; }

}
