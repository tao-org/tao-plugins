package ro.cs.tao.datasource.remote.creodias.landsat8;

import ro.cs.tao.datasource.remote.creodias.CreoDIASDataQuery;
import ro.cs.tao.datasource.remote.creodias.CreoDIASDataSource;
import ro.cs.tao.datasource.remote.creodias.parsers.Landsat8JsonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;

public class CreoDIASLandsat8Query extends CreoDIASDataQuery {

    public CreoDIASLandsat8Query(CreoDIASDataSource source) {
        super(source, "Landsat8");
    }

    @Override
    protected JSonResponseHandler<EOProduct> responseHandler() {
        return new Landsat8JsonResponseHandler();
    }

    @Override
    public String defaultId() { return "CreoDIAS Landsat-8 Query"; }

}
