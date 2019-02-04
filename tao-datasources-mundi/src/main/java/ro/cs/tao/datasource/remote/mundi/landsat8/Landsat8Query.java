package ro.cs.tao.datasource.remote.mundi.landsat8;

import ro.cs.tao.datasource.remote.mundi.BaseDataQuery;
import ro.cs.tao.datasource.remote.mundi.BaseDataSource;
import ro.cs.tao.datasource.remote.mundi.parsers.Landsat8ResponseHandler;

public class Landsat8Query extends BaseDataQuery {

    public Landsat8Query(BaseDataSource source) {
        super(source, "Landsat8");
    }

    @Override
    protected Landsat8ResponseHandler responseHandler(String countElement) {
        return new Landsat8ResponseHandler(countElement);
    }

    @Override
    public String defaultId() { return "CreoDIAS Landsat-8 Query"; }

}
