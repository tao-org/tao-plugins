package ro.cs.tao.datasource.remote.earthdata;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.earthdata.parameters.DateParameterConverter;
import ro.cs.tao.datasource.remote.earthdata.parameters.PolygonParameterConverter;
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

public class EarthDataQuery extends DataQuery {

    static {
        ConverterFactory factory = new ConverterFactory();
        factory.register(PolygonParameterConverter.class, Polygon2D.class);
        factory.register(DateParameterConverter.class, LocalDateTime.class);

        converterFactory.put(EarthDataQuery.class, factory);
    }

    public EarthDataQuery(EarthDataSource source, String sensorName){
        super(source, sensorName);
    }

    @Override
    public String defaultId() {
        return "NasaQuery";
    }

    @Override
    public boolean supportsPaging() { return false; }

    @Override
    protected List<EOProduct> executeImpl() {
        List<EOProduct> results = new ArrayList<>();
        List<NameValuePair> params = new ArrayList<>();

        if (this.parameters.containsKey(CommonParameterNames.TILE)) {
            String tileValue = this.parameters.get(CommonParameterNames.TILE).getValueAsString();
            params.add(new BasicNameValuePair(getRemoteName("patternTileId"), String.valueOf(true)));
            if (tileValue.contains(",")) {
                String[] granuleIDs = tileValue.split(",");
                for (String granuleID : granuleIDs) {
                    params.add(new BasicNameValuePair(getRemoteName(CommonParameterNames.TILE), granuleID));
                }
            } else {
                params.add(new BasicNameValuePair(getRemoteName(CommonParameterNames.TILE), tileValue));
            }
        } else {
            for (Map.Entry<String, QueryParameter<?>> entry : this.parameters.entrySet()) {
                QueryParameter<?> parameter = entry.getValue();
                try {
                    params.add(new BasicNameValuePair(getRemoteName(parameter.getName()), getParameterValue(parameter)));
                } catch (ConversionException e) {
                    throw new QueryException(e.getMessage());
                }
            }
        }

        if (this.limit <= 0) {
            this.limit = DEFAULT_LIMIT;
        }
        if (this.pageSize <= 0) {
            this.pageSize = DEFAULT_PAGE_SIZE;
        }

        int page = Math.max(this.pageNumber, 1);
        int retrieved = 0;
        List<EOProduct> tmpResults;
        do {
            List<NameValuePair> queryParams = new ArrayList<>(params);
            queryParams.add(new BasicNameValuePair("page_size", String.valueOf(this.pageSize)));
            queryParams.add(new BasicNameValuePair("page_num", String.valueOf(page)));

            String queryUrl = this.source.getConnectionString() + "/granules.json";
            logger.fine(String.format("Executing query %s", queryUrl));

            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, queryUrl, this.source.getCredentials(), queryParams)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        JsonResponseParser<EOProduct> parser = new JsonResponseParser<EOProduct>(new EarthDataQueryResponseHandler(this.coverageFilter)) {
                            @Override
                            public String[] getExcludedAttributes() {
                                return new String[]{"keywords", "links", "services"};
                            }
                        };
                        tmpResults = parser.parse(EntityUtils.toString(response.getEntity()));
                        if (tmpResults != null) {
                            results.addAll(tmpResults);
                            retrieved += tmpResults.size();
                        }
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
        } while (retrieved > 0 && results.size() <= this.limit && !tmpResults.isEmpty());

        if (results.size() > this.limit) {
            logger.info(String.format("Query {%s-%s} returned %s products", this.source.getId(), this.sensorName, this.limit));
            return results.subList(0, this.limit);
        } else {
            logger.info(String.format("Query {%s-%s} returned %s products", this.source.getId(), this.sensorName, results.size()));
            return results;
        }

    }
}
