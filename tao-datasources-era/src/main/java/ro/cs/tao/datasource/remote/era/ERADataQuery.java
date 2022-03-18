package ro.cs.tao.datasource.remote.era;

import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.eodata.EOProduct;

import java.util.List;

public class ERADataQuery extends DataQuery {

    public ERADataQuery(DataSource<?, ?> source, String sensorName) {
        super(source, sensorName);
    }

    @Override
    protected List<EOProduct> executeImpl() {
        return null;
    }
}
