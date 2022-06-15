package ro.cs.tao.datasource.stac;

import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.eodata.EOProduct;

import java.util.List;

public class STACQuery extends DataQuery {

    public STACQuery(DataSource<?, ?> source, String sensorName) {
        super(source, sensorName);
    }

    @Override
    protected List<EOProduct> executeImpl() {
        return null;
    }
}
