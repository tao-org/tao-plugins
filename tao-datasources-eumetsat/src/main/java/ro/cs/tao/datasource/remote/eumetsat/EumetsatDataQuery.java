package ro.cs.tao.datasource.remote.eumetsat;

import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.opensearch.OpenSearchClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EumetsatDataQuery extends DataQuery {

    static {
        final ConverterFactory factory = new ConverterFactory();
        converterFactory.put(EumetsatDataQuery.class, factory);
    }

    public EumetsatDataQuery(EumetsatDataSource source, String sensorName) {
        super(source, sensorName);
    }

    @Override
    public String defaultId() {
        return "EUMETSAT Query";
    }


    @Override
    public boolean supportsPaging() {
        return true;
    }

    @Override
    protected List<EOProduct> executeImpl() {
        final Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, QueryParameter<?>> entry : this.parameters.entrySet()) {
            QueryParameter<?> parameter = entry.getValue();
            if (!parameter.isOptional() && !parameter.isInterval() && parameter.getValue() == null) {
                throw new QueryException(String.format("Parameter [%s] is required but no value is supplied", parameter.getName()));
            }
            if (parameter.isOptional() &&
                ((!parameter.isInterval() && parameter.getValue() == null) ||
                 (parameter.isInterval() && parameter.getMinValue() == null && parameter.getMaxValue() == null) || parameter.getType().getName().contains("Date"))) {
                continue;
            }
            try {
                params.put(getRemoteName(parameter.getName()), URLEncoder.encode(getParameterValue(parameter), StandardCharsets.US_ASCII));
            } catch (ConversionException e) {
                throw new QueryException(e.getMessage());
            }
        }
        params.put("pi", getSensorName());
        final OpenSearchClient openSearchClient = ((EumetsatDataSource) this.source).getClient();
        try {
            return openSearchClient.search(this.sensorName, params);
        } catch (Exception e) {
            throw new QueryException(e);
        }
    }
}
