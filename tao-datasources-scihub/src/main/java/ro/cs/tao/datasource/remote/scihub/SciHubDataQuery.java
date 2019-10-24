/*
 * Copyright (C) 2018 CS ROMANIA
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
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.ProductHelper;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.datasource.remote.result.xml.XmlResponseParser;
import ro.cs.tao.datasource.remote.scihub.json.SciHubJsonResponseHandler;
import ro.cs.tao.datasource.remote.scihub.parameters.DoubleParameterConverter;
import ro.cs.tao.datasource.remote.scihub.parameters.SciHubPolygonParameterConverter;
import ro.cs.tao.datasource.remote.scihub.xml.SciHubXmlCountResponseHandler;
import ro.cs.tao.datasource.remote.scihub.xml.SciHubXmlResponseHandler;
import ro.cs.tao.datasource.util.HttpMethod;
import ro.cs.tao.datasource.util.NetUtils;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.products.sentinels.Sentinel2TileExtent;
import ro.cs.tao.products.sentinels.SentinelProductHelper;

import java.io.IOException;
import java.lang.reflect.Array;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class SciHubDataQuery extends DataQuery {

    //private static final String PATTERN_DATE = "NOW";
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
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
        Map<String, EOProduct> results = new LinkedHashMap<>();
        Map<String, String> queries = buildQueriesParams();
        final boolean isS2 = "Sentinel-2".equals(this.parameters.get(CommonParameterNames.PLATFORM).getValue());
        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append(String.format("Query {%s-%s} has %d subqueries: ", this.source.getId(), this.sensorName, queries.size()));
        for (String queryId : queries.keySet()) {
            msgBuilder.append(queryId).append(",");
        }
        msgBuilder.setLength(msgBuilder.length() - 1);
        logger.fine(msgBuilder.toString());
        final int actualLimit = this.limit > 0 ? this.limit : DEFAULT_LIMIT;
        msgBuilder.setLength(0);
        if (this.pageSize <= 0) {
            this.pageSize = Math.min(actualLimit, DEFAULT_PAGE_SIZE);
        } else {
            this.pageSize = Math.min(actualLimit, this.pageSize);
        }
        if (this.pageNumber == 1) {
            this.pageNumber = -1;
        }
        int page = Math.max(this.pageNumber, 1);
        List<EOProduct> tmpResults;
        for (Map.Entry<String, String> query : queries.entrySet()) {
            long i = page;
            long count;
            do {
                count = 0;
                List<NameValuePair> params = new ArrayList<>();
                if (this.source.getConnectionString().contains("dhus")) {
                    params.add(new BasicNameValuePair("limit", String.valueOf(pageSize)));
                    params.add(new BasicNameValuePair("offset", String.valueOf((i - 1) * pageSize)));
                    params.add(new BasicNameValuePair("filter", query.getValue()));
                } else {
                    params.add(new BasicNameValuePair("rows", String.valueOf(pageSize)));
                    params.add(new BasicNameValuePair("start", String.valueOf((i - 1) * pageSize)));
                    params.add(new BasicNameValuePair("q", query.getValue()));
                }
                params.add(new BasicNameValuePair("orderby", "beginposition asc"));
                String queryUrl = this.source.getConnectionString() + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
                final boolean hasProcessingDate = "Sentinel2".equals(this.sensorName) || "Sentinel3".equals(this.sensorName);
                logger.fine(String.format("Executing query %s: %s", query.getKey(), queryUrl));
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
                            if (tmpResults != null) {
                                count = tmpResults.size();
                                if (count > 0) {
                                    if (hasProcessingDate && this.parameters.containsKey(CommonParameterNames.CLOUD_COVER)) {
                                        final double clouds = Double.parseDouble(this.parameters.get(CommonParameterNames.CLOUD_COVER).getValue().toString());
                                        tmpResults = tmpResults.stream()
                                                .filter(r -> Double.parseDouble(r.getAttributeValue(isXml ? "cloudcoverpercentage" : "Cloud Cover Percentage")) <= clouds)
                                                .collect(Collectors.toList());
                                    }
                                    ProductHelper productHelper;
                                    for (EOProduct result : tmpResults) {
                                        if (!results.containsKey(result.getName())) {
                                            productHelper = SentinelProductHelper.create(result.getName());
                                            if (isS2) {
                                                if (result.getAttributeValue("tileid") == null) {
                                                    result.addAttribute("tiles", ((Sentinel2ProductHelper) productHelper).getTileIdentifier());
                                                } else {
                                                    result.addAttribute("tiles", result.getAttributeValue("tileid"));
                                                }
                                            }
                                            if (hasProcessingDate) {
                                                String dateString = productHelper.getProcessingDate();
                                                if (dateString != null) {
                                                    try {
                                                        result.setProcessingDate(dateFormat.parse(dateString));
                                                    } catch (java.text.ParseException ignored) {
                                                    }
                                                }
                                            }
                                            results.put(result.getName(), result);
                                        }
                                    }
                                }
                                logger.info(String.format("Query %s page %d returned %s products", query.getKey(), i, tmpResults.size()));
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
                i++;
            } while (this.pageNumber == -1 && count > 0 && results.size() < actualLimit);
        }
        logger.info(String.format("Query {%s-%s} returned %s products", this.source.getId(), this.sensorName, results.size()));
        return new ArrayList<>(results.values());
    }

    @Override
    public long getCount() {
        long count = 0;
        Map<String, String> queries = buildQueriesParams();
        //final int size = queries.size();
        final String countUrl = this.source.getProperty("scihub.search.count.url");
        if (countUrl != null) {
            for (Map.Entry<String, String> query : queries.entrySet()) {
                List<NameValuePair> params = new ArrayList<>();
                if (countUrl.contains("dhus")) {
                    params.add(new BasicNameValuePair("limit", "1"));
                    params.add(new BasicNameValuePair("offset", "0"));
                    params.add(new BasicNameValuePair("filter", query.getValue()));
                } else {
                    params.add(new BasicNameValuePair("rows", "1"));
                    params.add(new BasicNameValuePair("start", "0"));
                    params.add(new BasicNameValuePair("q", query.getValue()));
                }
                String queryUrl = countUrl + "?" + URLEncodedUtils.format(params, "UTF-8").replace("+", "%20");
                logger.fine(String.format("Executing query %s: %s", query.getKey(), queryUrl));
                try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, this.source.getCredentials())) {
                    switch (response.getStatusLine().getStatusCode()) {
                        case 200:
                            String rawResponse = EntityUtils.toString(response.getEntity());
                            ResponseParser<Long> parser = new XmlResponseParser<>();
                            ((XmlResponseParser) parser).setHandler(new SciHubXmlCountResponseHandler("totalResults"));
                            List<Long> result = parser.parse(rawResponse);
                            logger.fine(String.format(QUERY_RESULT_MESSAGE, query.getKey(),
                                                      this.source.getId(), this.sensorName, 1, count));
                            count += result.size() > 0 ? result.get(0) : 0;
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
    public String defaultId() { return "SciHubQuery"; }

    private Map<String, String> buildQueriesParams() {
        Map<String, String> queries = new HashMap<>();
        if (!this.parameters.containsKey(CommonParameterNames.PLATFORM)) {
            addParameter(CommonParameterNames.PLATFORM, this.dataSourceParameters.get(CommonParameterNames.PLATFORM).getDefaultValue());
        }
        String[] footprints = new String[0];
        final boolean isS1 = "Sentinel-1".equals(this.parameters.get(CommonParameterNames.PLATFORM).getValue());
        final boolean canUseTileParameter = this.parameters.containsKey(CommonParameterNames.TILE) && !isS1;
        if (this.parameters.containsKey(CommonParameterNames.TILE) && !isS1) {
            final QueryParameter tileParameter = this.parameters.get(CommonParameterNames.TILE);
            final Object value = tileParameter.getValue();
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
                        if (polygon != null) {
                            footprints = new String[]{polygon.toWKT()};
                        } else {
                            logger.warning(String.format("No extent found for tile '%s'. Maybe it is not a Sentinel-2 product",
                                                         strVal));
                        }
                    }
                }
            }
        }
        if (this.parameters.containsKey(CommonParameterNames.FOOTPRINT) && footprints.length == 0) {
            Polygon2D polygon = (Polygon2D) this.parameters.get(CommonParameterNames.FOOTPRINT).getValue();
            footprints = polygon.toWKTArray();
        }
        StringBuilder query = new StringBuilder();
        if (this.parameters.containsKey(CommonParameterNames.PRODUCT)) {
            String productName = this.parameters.get(CommonParameterNames.PRODUCT).getValueAsString();
            if (!productName.endsWith(".SAFE")) {
                productName += ".SAFE";
            }
            query.append(getRemoteName(CommonParameterNames.PRODUCT)).append(":").append(productName);
            queries.put(UUID.randomUUID().toString(), query.toString());
            query.setLength(0);
        } else {
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
                    switch (parameter.getName()) {
                        /*case CommonParameterNames.PRODUCT:
                            query.append(parameter.getValueAsString());
                            break;*/
                        case CommonParameterNames.FOOTPRINT:
                            if (!canUseTileParameter) {
                                query.append(getRemoteName(entry.getKey())).append(":");
                                try {
                                    QueryParameter<Polygon2D> fakeParam = new QueryParameter<>(Polygon2D.class,
                                                                                               "footprint",
                                                                                               Polygon2D.fromWKT(footprint));
                                    query.append(converterFactory.create(fakeParam).stringValue());
                                } catch (ConversionException e) {
                                    throw new QueryException(e.getMessage());
                                }
                            } else {
                                idx--;
                            }
                            break;
                        case CommonParameterNames.TILE:
                            String value = parameter.getValueAsString();
                            if (value.startsWith("[") && value.endsWith("]")) {
                                String[] values = value.substring(1, value.length() - 1).split(",");
                                query.append("(");
                                for (int i = 0; i < values.length; i++) {
                                    if (isS1) {
                                        query.append("relativeOrbitNumber:").append(values[i]);
                                    } else {
                                        query.append("filename:*").append(values[i]).append("*");
                                    }
                                    if (i < values.length - 1) {
                                        query.append(" OR ");
                                    }
                                }
                                query.append(")");
                            } else {
                                if (isS1) {
                                    query.append("relativeOrbitNumber:").append(value);
                                } else {
                                    query.append("filename:*").append(value).append("*");
                                }
                            }
                            break;
                        default:
                            if (parameter.getType().isArray()) {
                                query.append("(");
                                Object values = parameter.getValue();
                                int length = Array.getLength(values);
                                for (int i = 0; i < length; i++) {
                                    query.append(Array.get(values, i).toString());
                                    if (i < length - 1) {
                                        query.append(" OR ");
                                    }
                                }
                                if (length > 1) {
                                    query = new StringBuilder(query.substring(0, query.length() - 4));
                                }
                                query.append(")");
                            } else if (Date.class.equals(parameter.getType())) {
                                query.append(getRemoteName(entry.getKey())).append(":[");
                                try {
                                    query.append(converterFactory.create(parameter).stringValue());
                                } catch (ConversionException e) {
                                    throw new QueryException(e.getMessage());
                                }
                                query.append("]");
                            } else {
                                query.append(getRemoteName(entry.getKey())).append(":");
                                try {
                                    query.append(converterFactory.create(parameter).stringValue());
                                } catch (ConversionException e) {
                                    throw new QueryException(e.getMessage());
                                }
                            }
                            break;
                    }
                    idx++;
                }
                queries.put(UUID.randomUUID().toString(), query.toString());
                query.setLength(0);
            }
        }
        return queries;
    }

   /* private String[] splitMultiPolygon(String wkt) {
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
    }*/
}
