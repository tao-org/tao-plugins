package ro.cs.tao.datasource.remote.mundi.sentinel1;

import ro.cs.tao.datasource.remote.mundi.BaseDataQuery;
import ro.cs.tao.datasource.remote.mundi.BaseDataSource;
import ro.cs.tao.datasource.remote.mundi.parsers.Sentinel1ResponseHandler;

public class Sentinel1Query extends BaseDataQuery {

    public Sentinel1Query(BaseDataSource source) {
        super(source, "Sentinel1");
    }

    @Override
    protected Sentinel1ResponseHandler responseHandler(String countElement) {
        return new Sentinel1ResponseHandler(countElement);
    }

    @Override
    public String defaultId() { return "Mundi DIAS Sentinel-1 Query"; }

}
