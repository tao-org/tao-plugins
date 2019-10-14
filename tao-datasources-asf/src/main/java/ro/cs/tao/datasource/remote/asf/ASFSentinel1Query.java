package ro.cs.tao.datasource.remote.asf;

import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.eodata.EOProduct;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

public class ASFSentinel1Query extends DataQuery {

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
    private static final ConverterFactory converterFactory = ConverterFactory.getInstance();

    public ASFSentinel1Query(ASFSentinel1DataSource source) {
        super(source, "Sentinel1");
    }

    @Override
    public String defaultId() {
        return "ASFSentinel1Query";
    }

    protected List<EOProduct> executeImpl() {
        return null;
    }
}
