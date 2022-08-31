package ro.cs.tao.datasource.remote.mundi;

import org.apache.http.NameValuePair;
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
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.mundi.landsat8.Landsat8Query;
import ro.cs.tao.datasource.remote.mundi.sentinel1.Sentinel1Query;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.xml.XmlResponseParser;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.sentinels.Sentinel2TileExtent;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.*;

public abstract class BaseDataQuery extends DataQuery {

    protected final String connectionString;

    protected BaseDataQuery(MundiDataSource source, String sensorName, String connectionString) {
        super(source, sensorName);
        this.sensorName = sensorName;
        this.connectionString = connectionString;
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        final List<EOProduct> results = Collections.synchronizedList(new ArrayList<>());
        final Set<String> productNames = Collections.synchronizedSet(new HashSet<>());
        final List<List<BasicNameValuePair>> queries = buildQueriesParams();
        if (this.pageSize <= 0) {
            this.pageSize = Math.min(this.limit > 0 ? this.limit : DEFAULT_LIMIT, DEFAULT_LIMIT);
        }
        int page = Math.max(this.pageNumber, 1);
        final LocalDateTime start;
        if (this.parameters.containsKey(CommonParameterNames.START_DATE)) {
            start = (LocalDateTime) this.parameters.get(CommonParameterNames.START_DATE).getValue();
        } else {
            start = null;
        }
        final LocalDateTime end;
        if (this.parameters.containsKey(CommonParameterNames.END_DATE)) {
            end = (LocalDateTime) this.parameters.get(CommonParameterNames.END_DATE).getValue();
        } else {
            end = null;
        }
        final String level;
        if (this.parameters.containsKey("processingLevel")) {
            level = (String) this.parameters.get("processingLevel").getValue();
        } else {
            level = null;
        }
        final long count = getCount();
        final int maxPage = Math.max((int) (count / pageSize) + 1, page + 1);
        final BasicNameValuePair maxRecords = new BasicNameValuePair("maxRecords", String.valueOf(pageSize));
        for (List<BasicNameValuePair> query : queries) {
            //Parallel.For(page, maxPage, (i, cancelSignal) -> {
            for (int i = page; i < maxPage; i++) {
                if (limit > 0 && results.size() > limit) {
                    //cancelSignal.signal();
                    //return;
                    break;
                }
                final List<NameValuePair> params = new ArrayList<>();
                params.add(maxRecords);
                params.add(new BasicNameValuePair("startIndex", String.valueOf((i - 1) * pageSize + 1)));
                params.addAll(query);
                final String queryUrl = this.connectionString + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
                logger.finest(String.format("Executing query: %s", queryUrl));
                final List<EOProduct> tmpResults;
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, this.source.getCredentials())) {
                    switch (response.getStatusLine().getStatusCode()) {
                        case 200:
                            final String rawResponse = EntityUtils.toString(response.getEntity());
                            final ResponseParser<EOProduct> parser = new XmlResponseParser<>();
                            ((XmlResponseParser) parser).setHandler(responseHandler(null));
                            tmpResults = parser.parse(rawResponse);
                            if (tmpResults != null && tmpResults.size() > 0) {
                                tmpResults.removeIf(p ->  (start != null && start.isAfter(p.getAcquisitionDate()) ||
                                                          (end != null && end.isBefore(p.getAcquisitionDate())) ||
                                                          (this instanceof Landsat8Query && level != null && !p.getName().contains(level))));
                                synchronized (results) {
                                    final int currentSize = results.size();
                                    if (limit > 0 && currentSize + tmpResults.size() > limit) {
                                        int remaining = limit - currentSize;
                                        for (int j = 0; j < remaining; j++) {
                                            if (!productNames.contains(tmpResults.get(j).getName())) {
                                                productNames.add(tmpResults.get(j).getName());
                                                results.add(tmpResults.get(j));
                                            }
                                        }
                                    } else {
                                        for (EOProduct result : tmpResults) {
                                            if (!productNames.contains(result.getName())) {
                                                productNames.add(result.getName());
                                                results.add(result);
                                            }
                                        }
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
            }//);
        }
        productNames.clear();
        results.sort(Comparator.comparing(EOProduct::getAcquisitionDate));
        logger.info(String.format("Query returned %s products", results.size()));
        return results;
    }

    @Override
    protected long getCountImpl() {
        long count = 0;
        List<List<BasicNameValuePair>> queries = buildQueriesParams();
        final String countUrl = this.connectionString;
        if (countUrl != null) {
            for (List<BasicNameValuePair> query : queries) {
                final List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("maxRecords", "1"));
                params.add(new BasicNameValuePair("startIndex", "1"));
                params.addAll(query);

                final String queryUrl = countUrl + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
                logger.fine(String.format("Executing query: %s", queryUrl));
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, this.source.getCredentials())) {
                    switch (response.getStatusLine().getStatusCode()) {
                        case 200:
                            final String rawResponse = EntityUtils.toString(response.getEntity());
                            final ResponseParser<EOProduct> parser = new XmlResponseParser<>();
                            ((XmlResponseParser) parser).setHandler(responseHandler("totalResults"));
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

    protected abstract ro.cs.tao.datasource.remote.result.xml.XmlResponseHandler<EOProduct> responseHandler(String countElement);

    protected String[] getFootprintsFromTileParameter() {
        String[] footprints = null;
        final QueryParameter tileParameter = this.parameters.get(CommonParameterNames.TILE);
        final Object value = tileParameter.getValue();
        if (value != null) {
            if (value.getClass().isArray()) {
                footprints = new String[Array.getLength(value)];
                Polygon2D polygon;
                for (int i = 0; i < footprints.length; i++) {
                    polygon = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(Array.get(value, i).toString()));
                    footprints[i] = polygon.toWKT();
                }
            } else {
                final String strVal = tileParameter.getValueAsString();
                if (strVal.startsWith("[") && strVal.endsWith("]")) {
                    final String[] values = strVal.substring(1, strVal.length() - 1).split(",");
                    footprints = new String[values.length];
                    Polygon2D polygon;
                    for (int i = 0; i < values.length; i++) {
                        polygon = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(strVal));
                        footprints[i] = polygon.toWKT();
                    }
                } else {
                    Polygon2D polygon = Polygon2D.fromPath2D(Sentinel2TileExtent.getInstance().getTileExtent(strVal));
                    if (polygon != null) {
                        footprints = new String[]{polygon.toWKT()};
                    }
                }
            }
        }
        return footprints;
    }

    private List<List<BasicNameValuePair>> buildQueriesParams() {
        final List<List<BasicNameValuePair>> queries = new ArrayList<>();
        String[] footprints = new String[0];
        if (this.parameters.containsKey(CommonParameterNames.TILE)) {
            footprints = getFootprintsFromTileParameter();
        }
        if (this.parameters.containsKey(CommonParameterNames.FOOTPRINT)) {
            String wkt = ((Polygon2D) this.parameters.get(CommonParameterNames.FOOTPRINT).getValue()).toWKT();
            footprints = splitMultiPolygon(wkt);
        }
        for (String footprint : footprints) {
            final List<BasicNameValuePair> query = new ArrayList<>();
            for (Map.Entry<String, QueryParameter<?>> entry : this.parameters.entrySet()) {
                final QueryParameter<?> parameter = entry.getValue();
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
                        query.add(new BasicNameValuePair(getRemoteName(CommonParameterNames.PRODUCT), parameter.getValueAsString()));
                        break;
                    case CommonParameterNames.FOOTPRINT:
                        query.add(new BasicNameValuePair(getRemoteName(CommonParameterNames.FOOTPRINT), footprint));
                        break;
                    case CommonParameterNames.TILE:
                        if (this instanceof Sentinel1Query) {
                            query.add(new BasicNameValuePair(getRemoteName(CommonParameterNames.TILE), parameter.getValueAsString()));
                        } else {
                            query.add(new BasicNameValuePair(getRemoteName(CommonParameterNames.TILE), "*" + parameter.getValueAsString() + "*"));
                        }
                        break;
                    case CommonParameterNames.START_DATE:
                        QueryParameter<LocalDateTime> casted = (QueryParameter<LocalDateTime>) parameter;
                        try {
                            if (casted.isInterval()) {
                                casted.setValue(casted.getMinValue());
                                casted.setMinValue(null);
                                casted.setMaxValue(null);
                            }
                            query.add(new BasicNameValuePair(getRemoteName(entry.getKey()),
                                                             getParameterValue(parameter)));
                        } catch (ConversionException e) {
                            throw new QueryException(e.getMessage());
                        }
                        break;
                    case CommonParameterNames.END_DATE:
                        casted = (QueryParameter<LocalDateTime>) parameter;
                        try {
                            if (casted.isInterval()) {
                                casted.setValue(casted.getMaxValue());
                                casted.setMinValue(null);
                                casted.setMaxValue(null);
                            }
                            query.add(new BasicNameValuePair(getRemoteName(entry.getKey()),
                                                             getParameterValue(parameter)));
                        } catch (ConversionException e) {
                            throw new QueryException(e.getMessage());
                        }
                        break;
                    default:
                        if (parameter.getType().isArray()) {
                            final StringBuilder builder = new StringBuilder();
                            final Object value = parameter.getValue();
                            final int length = Array.getLength(value);
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
                        } else if (LocalDateTime.class.equals(parameter.getType())) {
                            try {
                                query.add(new BasicNameValuePair(getRemoteName(entry.getKey()), getParameterValue(parameter)));
                            } catch (ConversionException e) {
                                throw new QueryException(e.getMessage());
                            }
                        } else {
                            try {
                                query.add(new BasicNameValuePair(getRemoteName(entry.getKey()), getParameterValue(parameter)));
                            } catch (ConversionException e) {
                                throw new QueryException(e.getMessage());
                            }
                        }
                }
            }
            queries.add(query);
        }
        return queries;
    }

    private String[] splitMultiPolygon(String wkt) {
        String[] polygons = null;
        try {
            final WKTReader reader = new WKTReader();
            final Geometry geometry = reader.read(wkt);
            if (geometry instanceof MultiPolygon) {
                final MultiPolygon mPolygon = (MultiPolygon) geometry;
                int n = mPolygon.getNumGeometries();
                polygons = new String[n];
                for (int i = 0; i < n; i++) {
                    polygons[i] = mPolygon.getGeometryN(i).toText();
                }
            } else {
                polygons = new String[]{wkt};
            }
        } catch (ParseException e) {
            logger.severe(e.getMessage());
        }
        return polygons;
    }
}