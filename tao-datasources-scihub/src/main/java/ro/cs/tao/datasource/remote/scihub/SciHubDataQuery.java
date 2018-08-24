/*
 * Copyright (C) 2017 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package ro.cs.tao.datasource.remote.scihub;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.converters.DateParameterConverter;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.datasource.remote.result.xml.XmlResponseParser;
import ro.cs.tao.datasource.remote.scihub.json.SciHubJsonResponseHandler;
import ro.cs.tao.datasource.remote.scihub.parameters.DoubleParameterConverter;
import ro.cs.tao.datasource.remote.scihub.xml.SciHubXmlResponseHandler;
import ro.cs.tao.datasource.util.HttpMethod;
import ro.cs.tao.datasource.util.NetUtils;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.sentinels.Sentinel2TileExtent;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class SciHubDataQuery extends DataQuery {

    private static final String PATTERN_DATE = "NOW";
    private static final String PATTERN_OFFSET_DATE = PATTERN_DATE + "-%sDAY";
    private static final ConverterFactory converterFactory = ConverterFactory.getInstance();

    private final String sensorName;

    static {
        converterFactory.register(SciHubPolygonParameterConverter.class, Polygon2D.class);
        converterFactory.register(DateParameterConverter.class, Date.class);
        converterFactory.register(DoubleParameterConverter.class, Double.class);
    }

    SciHubDataQuery(SciHubDataSource source, String sensorName) {
        super(source, sensorName);
        this.sensorName = sensorName;
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        List<EOProduct> results = new ArrayList<>();
        List<String> queries = buildQueriesParams();
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
        for (String query : queries) {
            boolean canContinue = true;
            for (long i = page; actualCount < count * queriesNo && canContinue; i++) {
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("q", query));
                params.add(new BasicNameValuePair("orderby", "beginposition asc"));
                params.add(new BasicNameValuePair("rows", String.valueOf(pageSize)));
                params.add(new BasicNameValuePair("start", String.valueOf((i - 1) * pageSize + 1)));
                String queryUrl = this.source.getConnectionString() + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
                logger.info(String.format("Executing query: %s", queryUrl));
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, this.source.getCredentials())) {
                    switch (response.getStatusLine().getStatusCode()) {
                        case 200:
                            String rawResponse = EntityUtils.toString(response.getEntity());
                            ResponseParser<EOProduct> parser;
                            boolean isXml = rawResponse.startsWith("<?xml");
                            if (isXml) {
                                parser = new XmlResponseParser<>();
                                ((XmlResponseParser) parser).setHandler(new SciHubXmlResponseHandler("entry"));
                            } else {
                                parser = new JsonResponseParser<>(new SciHubJsonResponseHandler());
                            }
                            tmpResults = parser.parse(rawResponse);
                            canContinue = tmpResults != null && tmpResults.size() > 0 && count != this.pageSize;
                            if (tmpResults != null) {
                                actualCount += tmpResults.size();
                                if ("Sentinel2".equals(this.sensorName) &&
                                        this.parameters.containsKey("cloudcoverpercentage")) {
                                    final Double clouds = Double.parseDouble(this.parameters.get("cloudcoverpercentage").getValue().toString());
                                    tmpResults = tmpResults.stream()
                                            .filter(r -> Double.parseDouble(r.getAttributeValue(isXml ? "cloudcoverpercentage" : "Cloud Cover Percentage")) <= clouds)
                                            .collect(Collectors.toList());
                                }
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
    public long getCount() {
        long count = 0;
        List<String> queries = buildQueriesParams();
        final int size = queries.size();
        final String countUrl = this.source.getProperty("scihub.search.count.url");
        if (countUrl != null) {
            for (int i = 0; i < size; i++) {
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("filter", queries.get(i)));
                String queryUrl = countUrl + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, this.source.getCredentials())) {
                    switch (response.getStatusLine().getStatusCode()) {
                        case 200:
                            String rawResponse = EntityUtils.toString(response.getEntity());
                            long qCount = Long.parseLong(rawResponse);
                            //logger.info(String.format("Query %s of %s [%s]: %s", i + 1, size, qCount, queryUrl));
                            count += qCount;
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

    @Override
    public String defaultName() { return "SciHubQuery"; }

    private List<String> buildQueriesParams() {
        List<String> queries = new ArrayList<>();
        if (!this.parameters.containsKey("platformName")) {
            addParameter("platformName", this.supportedParams.get("platformName").getDefaultValue());
        }
        String[] footprints = new String[1];
        if (this.parameters.containsKey("tileId")) {
            Rectangle2D rectangle2D = Sentinel2TileExtent.getInstance()
                                               .getTileExtent(this.parameters.get("tileId").getValueAsString());
            Polygon2D polygon = new Polygon2D();
            polygon.append(rectangle2D.getMinX(), rectangle2D.getMinY());
            polygon.append(rectangle2D.getMaxX(), rectangle2D.getMinY());
            polygon.append(rectangle2D.getMaxX(), rectangle2D.getMaxY());
            polygon.append(rectangle2D.getMinX(), rectangle2D.getMaxY());
            polygon.append(rectangle2D.getMinX(), rectangle2D.getMinY());
            footprints[0] = polygon.toWKT();
        } else if (this.parameters.containsKey("footprint")) {
            String wkt = ((Polygon2D) this.parameters.get("footprint").getValue()).toWKT();
            footprints = splitMultiPolygon(wkt);
        }
        StringBuilder query = new StringBuilder();
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
                if (idx > 0) {
                    query.append(" AND ");
                }
                if (parameter.getName().equals("product")) {
                    query.append(parameter.getValueAsString());
                    break;
                } else if (parameter.getName().equals("footprint")) {
                    query.append(entry.getKey()).append(":");
                    try {
                        QueryParameter<Polygon2D> fakeParam = new QueryParameter<>(Polygon2D.class,
                                                                                   "footprint",
                                                                                   Polygon2D.fromWKT(footprint));
                        query.append(converterFactory.create(fakeParam).stringValue());
                    } catch (ConversionException e) {
                        throw new QueryException(e.getMessage());
                    }
                } else if (parameter.getType().isArray()) {
                    query.append("(");
                    Object value = parameter.getValue();
                    int length = Array.getLength(value);
                    for (int i = 0; i < length; i++) {
                        query.append(Array.get(value, i).toString());
                        if (i < length - 1) {
                            query.append(" OR ");
                        }
                    }
                    if (length > 1) {
                        query = new StringBuilder(query.substring(0, query.length() - 4));
                    }
                    query.append(")");
                } else if (Date.class.equals(parameter.getType())) {
                    query.append(entry.getKey()).append(":[");
                    try {
                        query.append(converterFactory.create(parameter).stringValue());
                    } catch (ConversionException e) {
                        throw new QueryException(e.getMessage());
                    }
                    query.append("]");
                } else {
                    query.append(entry.getKey()).append(":");
                    try {
                        query.append(converterFactory.create(parameter).stringValue());
                    } catch (ConversionException e) {
                        throw new QueryException(e.getMessage());
                    }
                }
                idx++;
            }
            queries.add(query.toString());
            logger.info(String.format("%s\t%s\t%s",
                                      footprint != null ? Sentinel2TileExtent.getInstance().intersectingTiles(Polygon2D.fromWKT(footprint)) : "n/a",
                                      footprint != null ? footprint : "n/a",
                                      query.toString()));
            query.setLength(0);
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
