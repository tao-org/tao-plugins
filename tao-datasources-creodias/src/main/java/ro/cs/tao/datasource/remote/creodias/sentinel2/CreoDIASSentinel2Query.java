package ro.cs.tao.datasource.remote.creodias.sentinel2;

import ro.cs.tao.datasource.remote.creodias.CreoDIASDataQuery;
import ro.cs.tao.datasource.remote.creodias.CreoDIASDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.Sentinel2JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

public class CreoDIASSentinel2Query extends CreoDIASDataQuery {

    public CreoDIASSentinel2Query(CreoDIASDataSource source) {
        super(source, "Sentinel2");
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Sentinel2JsonResponseHandler();
    }

    @Override
    public String defaultId() { return "CreoDIAS Sentinel-2 Query"; }

}
