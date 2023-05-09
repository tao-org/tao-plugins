package ro.cs.tao.datasource.remote.theia;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.*;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TheiaDataQuery extends DataQuery {

    static {
        ConverterFactory factory = new ConverterFactory();
        factory.register(PolygonParameterConverter.class, Polygon2D.class);
        factory.register(DateParameterConverter.class, LocalDateTime.class);
        factory.register(RangeParameterConverter.class, Double.class);
        converterFactory.put(TheiaDataQuery.class, factory);
    }

    public TheiaDataQuery(TheiaDataSource source, String sensorName) {
        super(source, sensorName);
    }

    @Override
    protected List<EOProduct> executeImpl() {
        final Header header = ((TheiaDataSource) this.source).authenticate();
        List<EOProduct> results = new ArrayList<>();
        List<NameValuePair> params = new ArrayList<>();
        final String collection = this.parameters.containsKey("collection") ?
                this.parameters.get("collection").getValueAsString() :
                (String) this.dataSourceParameters.get("collection").getDefaultValue();
        for (Map.Entry<String, QueryParameter<?>> entry : this.parameters.entrySet()) {
            QueryParameter<?> parameter = entry.getValue();
            if (!parameter.isOptional() && !parameter.isInterval() && parameter.getValue() == null) {
                throw new QueryException(String.format("Parameter [%s] is required but no value is supplied", parameter.getName()));
            }
            if (parameter.isOptional() &
                    ((!parameter.isInterval() & parameter.getValue() == null) |
                            (parameter.isInterval() & parameter.getMinValue() == null & parameter.getMaxValue() == null))) {
                continue;
            }
            try {
                if (!"collection".equals(parameter.getName())) {
                    params.add(new BasicNameValuePair(getRemoteName(parameter.getName()),
                            getParameterValue(parameter)));
                }
            } catch (ConversionException e) {
                throw new QueryException(e.getMessage());
            }
        }
        if (this.limit <= 0) {
            this.limit = DEFAULT_LIMIT;
        }
        if (this.pageSize <= 0) {
            this.pageSize = Math.min(this.limit, DEFAULT_LIMIT);
        }
        int page = Math.max(this.pageNumber, 1);
        int retrieved = 0;
        do {
            List<NameValuePair> queryParams = new ArrayList<>(params);
            queryParams.add(new BasicNameValuePair("maxRecords", String.valueOf(this.pageSize)));
            queryParams.add(new BasicNameValuePair("page", String.valueOf(page)));

            String queryUrl = TheiaDataSource.SEARCH_URL.replace("{collection}", collection) + "?"
                    + URLEncodedUtils.format(queryParams, "UTF-8").replace("+", "%20");
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, header, null)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        JsonResponseParser<EOProduct> parser = new JsonResponseParser<EOProduct>(new TheiaQueryResponseHandler()) {
                            @Override
                            public String[] getExcludedAttributes() {
                                return new String[] { "keywords", "links", "services" };
                            }
                        };
                        results.addAll(parser.parse(EntityUtils.toString(response.getEntity())));
                        page++;
                        break;
                    case 401:
                        throw new QueryException("The supplied credentials are invalid!");
                    default:
                        throw new QueryException(String.format("The request was not successful. Reason: %s",
                                response.getStatusLine().getReasonPhrase()));
                }
            } catch (IOException ex) {
                throw new QueryException(ex);
            }
        } while (this.pageNumber <= 1 && (retrieved > 0 && results.size() <= this.limit));
        if (results.size() > this.limit) {
            return results.subList(0, this.limit);
        } else {
            return results;
        }
    }

    @Override
    public String defaultId() {
        return "TheiaQuery";
    }

}
