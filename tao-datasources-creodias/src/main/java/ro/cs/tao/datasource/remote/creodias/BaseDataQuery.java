package ro.cs.tao.datasource.remote.creodias;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.creodias.parsers.DateParameterConverter;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.sentinels.Sentinel2TileExtent;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public abstract class BaseDataQuery extends DataQuery {

    protected final String connectionString;

    private static final ConverterFactory converterFactory = ConverterFactory.getInstance();

    static {
        converterFactory.register(DateParameterConverter.class, Date.class);
    }

    protected BaseDataQuery(CreoDiasDataSource source, String sensorName, String connectionString) {
        super(source, sensorName);
        this.sensorName = sensorName;
        this.connectionString = connectionString;
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        List<EOProduct> results = new ArrayList<>();
        List<List<BasicNameValuePair>> queries = buildQueriesParams();
        if (this.pageSize <= 0) {
            this.pageSize = Math.min(this.limit > 0 ? this.limit : DEFAULT_LIMIT, DEFAULT_LIMIT);
        }
        int page = Math.max(this.pageNumber, 1);
        long count = this.limit > 0 ? this.limit :
                this.pageNumber > 0 ? this.pageSize :
                        getCount();
        long actualCount = 0;
        List<EOProduct> tmpResults;
        final int queriesNo = queries.size();
        for (List<BasicNameValuePair> query : queries) {
            boolean canContinue = true;
            for (long i = page; actualCount < count * queriesNo && canContinue; i++) {
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("maxRecords", String.valueOf(pageSize)));
                params.add(new BasicNameValuePair("index", String.valueOf(1)));
                params.add(new BasicNameValuePair("page", String.valueOf(i)));
                params.add(new BasicNameValuePair("sortParam", "startDate"));
                params.add(new BasicNameValuePair("sortOrder", "asc"));
                params.addAll(query);
                String queryUrl = this.connectionString + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
                logger.fine(String.format("Executing query: %s", queryUrl));
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, this.source.getCredentials())) {
                    switch (response.getStatusLine().getStatusCode()) {
                        case 200:
                            String rawResponse = EntityUtils.toString(response.getEntity());
                            ResponseParser<EOProduct> parser = new JsonResponseParser<>(responseHandler());
                            tmpResults = parser.parse(rawResponse);
                            canContinue = tmpResults != null && tmpResults.size() > 0 && count != this.pageSize;
                            if (tmpResults != null) {
                                actualCount += tmpResults.size();
                                for (EOProduct result : tmpResults) {
                                    if (!results.contains(result)) {
                                        results.add(result);
                                    }
                                }
                            }
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
            }
        }
        logger.info(String.format("Query returned %s products", results.size()));
        return results;
    }

    @Override
    protected long getCountImpl() {
        long count = 0;
        List<List<BasicNameValuePair>> queries = buildQueriesParams();
        final int size = queries.size();
        final String countUrl = this.connectionString;
        if (countUrl != null) {
            for (List<BasicNameValuePair> query : queries) {
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("maxRecords", "1"));
                params.add(new BasicNameValuePair("index", "1"));
                params.add(new BasicNameValuePair("page", "1"));
                params.addAll(query);

                String queryUrl = countUrl + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
                logger.fine(String.format("Executing query: %s", queryUrl));
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, this.source.getCredentials())) {
                    switch (response.getStatusLine().getStatusCode()) {
                        case 200:
                            String rawResponse = EntityUtils.toString(response.getEntity());
                            ResponseParser<EOProduct> parser = new JsonResponseParser<>(responseHandler(), "totalResults");
                            count += parser.parseCount(rawResponse);
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
            }
        }
        return count;
    }

    protected abstract JSonResponseHandler<EOProduct> responseHandler();

    private List<List<BasicNameValuePair>> buildQueriesParams() {
        List<List<BasicNameValuePair>> queries = new ArrayList<>();
        String[] footprints = new String[0];
        if (this.parameters.containsKey(CommonParameterNames.TILE)) {
            QueryParameter tileParameter = this.parameters.get(CommonParameterNames.TILE);
            Object value = tileParameter.getValue();
            if (value != null) {
                if (value.getClass().isArray()) {
                    footprints = new String[Array.getLength(value)];
                    for (int i = 0; i < footprints.length; i++) {
                        Polygon2D polygon = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(Array.get(value, i).toString()));
                        footprints[i] = polygon.toWKT();
                    }
                } else {
                    String strVal = tileParameter.getValueAsString();
                    if (strVal.startsWith("[") && strVal.endsWith("]")) {
                        String[] values = strVal.substring(1, strVal.length() - 1).split(",");
                        footprints = new String[values.length];
                        for (int i = 0; i < values.length; i++) {
                            Polygon2D polygon = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(strVal));
                            footprints[i] = polygon.toWKT();
                        }
                    } else {
                        Polygon2D polygon = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(strVal));
                        footprints = new String[]{polygon.toWKT()};
                    }
                }
            }
        }
        if (this.parameters.containsKey(CommonParameterNames.FOOTPRINT)) {
            String wkt = ((Polygon2D) this.parameters.get(CommonParameterNames.FOOTPRINT).getValue()).toWKT();
            footprints = splitMultiPolygon(wkt);
        }
        List<BasicNameValuePair> query = new ArrayList<>();
        for (String footprint : footprints) {
            int idx = 0;
            for (Map.Entry<String, QueryParameter> entry : this.parameters.entrySet()) {
                QueryParameter parameter = entry.getValue();
                if (!parameter.isOptional() && !parameter.isInterval() && parameter.getValue() == null) {
                    throw new QueryException(String.format("Parameter [%s] is required but no value is supplied", parameter.getName()));
                }
                if (parameter.isOptional() &
                        ((!parameter.isInterval() & parameter.getValue() == null) |
                                (parameter.isInterval() & parameter.getMinValue() == null & parameter.getMaxValue() == null))) {
                    continue;
                }
                switch (parameter.getName()) {
                    case CommonParameterNames.PRODUCT:
                        query.add(new BasicNameValuePair(getRemoteName(CommonParameterNames.FOOTPRINT), parameter.getValueAsString()));
                        break;
                    case CommonParameterNames.FOOTPRINT:
                        try {
                            QueryParameter<Polygon2D> fakeParam = new QueryParameter<>(Polygon2D.class,
                                                                                       CommonParameterNames.FOOTPRINT,
                                                                                       Polygon2D.fromWKT(footprint));
                            query.add(new BasicNameValuePair(getRemoteName(entry.getKey()), converterFactory.create(fakeParam).stringValue()));
                        } catch (ConversionException e) {
                            throw new QueryException(e.getMessage());
                        }
                        break;
                    default:
                        if (parameter.getType().isArray()) {
                            StringBuilder builder = new StringBuilder();
                            Object value = parameter.getValue();
                            int length = Array.getLength(value);
                            for (int i = 0; i < length; i++) {
                                builder.append(Array.get(value, i).toString());
                                if (i < length - 1) {
                                    builder.append(",");
                                }
                            }
                            if (length > 1) {
                                builder.setLength(builder.length() - 1);
                            }
                            query.add(new BasicNameValuePair(getRemoteName(entry.getKey()), builder.toString()));
                        } else if (Date.class.equals(parameter.getType())) {
                            try {
                                query.add(new BasicNameValuePair(getRemoteName(entry.getKey()), converterFactory.create(parameter).stringValue()));
                            } catch (ConversionException e) {
                                throw new QueryException(e.getMessage());
                            }
                        } else {
                            try {
                                query.add(new BasicNameValuePair(getRemoteName(entry.getKey()), converterFactory.create(parameter).stringValue()));
                            } catch (ConversionException e) {
                                throw new QueryException(e.getMessage());
                            }
                        }
                }
                idx++;
            }
            queries.add(query);
        }
        return queries;
    }

    private String[] splitMultiPolygon(String wkt) {
        String[] polygons = null;
        try {
            WKTReader reader = new WKTReader();
            Geometry geometry = reader.read(wkt);
            if (geometry instanceof MultiPolygon) {
                MultiPolygon mPolygon = (MultiPolygon) geometry;
                int n = mPolygon.getNumGeometries();
                polygons = new String[n];
                for (int i = 0; i < n; i++) {
                    polygons[i] = mPolygon.getGeometryN(i).toText();
                }
            } else {
                polygons = new String[] { wkt };
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return polygons;
    }
}
