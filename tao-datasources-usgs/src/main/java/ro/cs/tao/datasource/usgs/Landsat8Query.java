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
package ro.cs.tao.datasource.usgs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.datasource.usgs.json.NameValuePair;
import ro.cs.tao.datasource.usgs.json.handlers.DatasetFieldsResponseHandler;
import ro.cs.tao.datasource.usgs.json.handlers.DownloadResponseHandler;
import ro.cs.tao.datasource.usgs.json.handlers.LoginResponseHandler;
import ro.cs.tao.datasource.usgs.json.handlers.SearchResponseHandler;
import ro.cs.tao.datasource.usgs.json.requests.*;
import ro.cs.tao.datasource.usgs.json.responses.LoginResponse;
import ro.cs.tao.datasource.usgs.parameters.USGSDateParameterConverter;
import ro.cs.tao.datasource.util.HttpMethod;
import ro.cs.tao.datasource.util.NetUtils;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.landsat.Landsat8TileExtent;
import ro.cs.tao.utils.StringUtilities;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Cosmin Cara
 */
public class Landsat8Query extends DataQuery {

    private static final ConverterFactory converterFactory = ConverterFactory.getInstance();
    private static final Map<String, Integer> fieldIds;
    private String apiKey;

    static {
        converterFactory.register(USGSDateParameterConverter.class, Date.class);
        fieldIds = new HashMap<>();
    }

    Landsat8Query(USGSDataSource source) {
        super(source, "Landsat8");
    }

    @Override
    public String defaultId() {
        return "USGSLandsat8Query";
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        Set<String> pathRows = getPathRows();
        try {
            return (List<EOProduct>) executeQuery(this.pageNumber, this.pageSize, pathRows, false);
        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    @Override
    protected long getCountImpl() {
        long retVal = 0;
        Set<String> pathRows = getPathRows();
        try {
            retVal = (long) executeQuery(1, 1, pathRows, true);
        } catch (Exception e) {
            logger.warning(e.getMessage());
        }
        return retVal;
    }

    private Set<String> getPathRows() {
        Set<String> pathRows = null;
        for (QueryParameter parameter : this.parameters.values()) {
            final Class parameterType = parameter.getType();
            final Object parameterValue = parameter.getValue();
            if (parameterType.isArray() && String[].class.isAssignableFrom(parameterType)) {
                // we have an array of rows and paths
                if (parameterValue != null) {
                    pathRows = new HashSet<>();
                    if (parameterValue instanceof String[]) {
                        Collections.addAll(pathRows, (String[]) parameterValue);
                    } else {
                        Collections.addAll(pathRows, StringUtilities.fromJsonArray(parameterValue.toString()));
                    }
                }
            } else  if (Polygon2D.class.equals(parameterType) &&
                    (pathRows == null || pathRows.size() == 0)) {
                Polygon2D footprint = (Polygon2D ) parameterValue;
                if (footprint != null) {
                    pathRows = Landsat8TileExtent.getInstance().intersectingTiles(footprint);
                }
            }
        }
        return pathRows;
    }

    private String authenticate() throws Exception {
        if (this.apiKey == null) {
            LoginRequest request = new LoginRequest();
            UsernamePasswordCredentials credentials = this.source.getCredentials();
            if (credentials == null) {
                throw new QueryException(String.format("Credentials not set for %s", this.source.getId()));
            }
            request.setUsername(credentials.getUserName());
            request.setPassword(credentials.getPassword());
            String url = buildPostRequestURL("login");
            List<org.apache.http.NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("jsonRequest", request.toString()));
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, url, null, params)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        String body = EntityUtils.toString(response.getEntity());
                        if (body == null) {
                            throw new QueryException("Cannot retrieve API key [empty response body]");
                        }
                        JsonResponseParser<LoginResponse> parser = new JsonResponseParser<>(new LoginResponseHandler());
                        LoginResponse login = parser.parseValue(body);
                        if (login == null) {
                            throw new QueryException("Cannot retrieve API key [empty response body]");
                        }
                        if (login.getErrorCode() == null) {
                            this.apiKey = login.getData();
                        }
                        if (this.apiKey == null) {
                            throw new QueryException(String.format("The API key could not be obtained [catalogId:%s,apiVersion:%s,errorCode:%s,error:%s,data:%s",
                                                                   login.getCatalog_id(), login.getApi_version(), login.getErrorCode(), login.getError(), login.getData()));
                        }
                        break;
                    case 401:
                        throw new QueryException("Cannot retrieve API key [401:not authorized]");
                    default:
                        throw new QueryException(String.format("The request was not successful. Reason: %s",
                                                               response.getStatusLine().getReasonPhrase()));
                }
                setupRemoteFields();
            } catch (Exception ex) {
                throw new QueryException(ex);
            }
        }
        return this.apiKey;
    }

    private void setupRemoteFields() throws Exception {
        if (fieldIds.size() == 0) {
            String apiKey = authenticate();
            DatasetFieldsRequest request = new DatasetFieldsRequest();
            request.setApiKey(apiKey);
            request.setDatasetName(this.parameters.containsKey(CommonParameterNames.PLATFORM) ?
                                           this.parameters.get(CommonParameterNames.PLATFORM).getValueAsString() :
                                           "LANDSAT_8_C1");
            String url = buildGetRequestURL("datasetfields", request);
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, url, null)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        String body = EntityUtils.toString(response.getEntity());
                        JsonResponseParser<FieldDescriptor> parser = new JsonResponseParser<>(new DatasetFieldsResponseHandler());
                        List<FieldDescriptor> params = parser.parse(body);
                        for (FieldDescriptor param : params) {
                            fieldIds.put(param.getName(), param.getFieldId());
                        }
                        break;
                    case 401:
                        throw new QueryException("The supplied credentials are invalid!");
                    default:
                        throw new QueryException(String.format("The request was not successful. Reason: %s",
                                                               response.getStatusLine().getReasonPhrase()));
                }
            } catch (Exception ex) {
                throw new QueryException(ex);
            }
        }
    }

    private List<EOProduct> resolveDownloadUrls(List<EOProduct> products) throws Exception {
        String apiKey = authenticate();
        DownloadRequest request = new DownloadRequest();
        request.setApiKey(apiKey);
        request.setDatasetName(this.parameters.containsKey(CommonParameterNames.PLATFORM) ?
                                       this.parameters.get(CommonParameterNames.PLATFORM).getValueAsString() :
                                       "LANDSAT_8_C1");
        int size = products.size();
        Map<String, Integer> indices = new HashMap<>(size);
        String[] ids = new String[size];
        for (int i = 0; i < size; i++) {
            ids[i] = products.get(i).getId();
            indices.put(ids[i], i);
        }
        request.setEntityIds(ids);
        String url = buildGetRequestURL("download", request);
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, url, null)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(response.getEntity());
                    JsonResponseParser<NameValuePair> parser = new JsonResponseParser<>(new DownloadResponseHandler());
                    List<NameValuePair> params = parser.parse(body);
                    for (NameValuePair param : params) {
                        products.get(indices.get(param.getName())).setLocation(param.getValue());
                    }
                    break;
                case 401:
                    throw new QueryException("The supplied credentials are invalid!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                                                           response.getStatusLine().getReasonPhrase()));
            }
        } catch (Exception ex) {
            throw new QueryException(ex);
        }
        return products;
    }

    private String buildGetRequestURL(String operation, Object jsonRequest) throws Exception {
        return this.source.getConnectionString()
                + operation + "?jsonRequest="
                + URLEncoder.encode(new ObjectMapper().writer().writeValueAsString(jsonRequest), "UTF-8");
    }

    private String buildPostRequestURL(String operation) throws Exception {
        return this.source.getConnectionString() + operation;
    }

    private String buildQueryUrl(int pgNumber, int pgSize, Set<String> pathRowFilter) throws Exception {
        if (!this.parameters.containsKey(CommonParameterNames.PLATFORM)) {
            addParameter(CommonParameterNames.PLATFORM, this.dataSourceParameters.get(CommonParameterNames.PLATFORM).getDefaultValue());
        }
        String apiKey = authenticate();
        SearchRequest request = new SearchRequest().withAPIKey(apiKey);
        SearchFilterValue filter;
        Map<String, QueryParameter> parameters = new HashMap<>(this.parameters);
        if ((parameters.containsKey("row") && parameters.containsKey("path")) ||
             parameters.containsKey(CommonParameterNames.TILE)) {
            parameters.remove(CommonParameterNames.FOOTPRINT);
        }
        for (QueryParameter parameter : parameters.values()) {
            try {
                switch (parameter.getName()) {
                    case CommonParameterNames.PLATFORM:
                        request.withDataSet(parameter.getValueAsString());
                        break;
                    case CommonParameterNames.START_DATE:
                        request.withStartDate(converterFactory.create(parameter).stringValue());
                        break;
                    case CommonParameterNames.END_DATE:
                        request.withEndDate(converterFactory.create(parameter).stringValue());
                        break;
                    case CommonParameterNames.FOOTPRINT:
                        Polygon2D footprint = (Polygon2D) parameter.getValue();
                        Rectangle2D bounds = footprint.getBounds2D();
                        request.withLowerLeft(bounds.getMinX(), bounds.getMinY())
                               .withUpperRight(bounds.getMaxX(), bounds.getMaxY());
                        break;
                    case CommonParameterNames.PRODUCT:
                        filter = new SearchFilterValue();
                        filter.setFieldId(fieldIds.get("Landsat Product Identifier"));
                        filter.setOperand("=");
                        filter.setValue(parameter.getValueAsString());
                        request.withFilter(filter);
                        break;
                    case CommonParameterNames.PRODUCT_TYPE:
                        filter = new SearchFilterValue();
                        filter.setFieldId(fieldIds.get("Data Type Level-1"));
                        filter.setOperand("=");
                        filter.setValue(parameter.getValueAsString());
                        request.withFilter(filter);
                        break;
                    case "minClouds":
                        request.withMinClouds(parameter.getValueAsInt());
                        break;
                    case "maxClouds":
                        request.withMaxClouds(parameter.getValueAsInt());
                        break;
                    case "row":
                        filter = new SearchFilterValue();
                        filter.setFieldId(fieldIds.get("WRS Row"));
                        filter.setOperand("=");
                        filter.setValue(parameter.getValueAsString());
                        request.withFilter(filter);
                        break;
                    case "path":
                        filter = new SearchFilterValue();
                        filter.setFieldId(fieldIds.get("WRS Path"));
                        filter.setOperand("=");
                        filter.setValue(parameter.getValueAsString());
                        request.withFilter(filter);
                        break;
                    case CommonParameterNames.TILE:
                        String pathRow = parameters.get(CommonParameterNames.TILE).getValueAsString();
                        filter = new SearchFilterValue();
                        filter.setFieldId(fieldIds.get("WRS Path"));
                        filter.setOperand("=");
                        filter.setValue(pathRow.substring(0, 3));
                        request.withFilter(filter);
                        filter = new SearchFilterValue();
                        filter.setFieldId(fieldIds.get("WRS Row"));
                        filter.setOperand("=");
                        filter.setValue(pathRow.substring(3, 6));
                        request.withFilter(filter);
                        break;
                }
            } catch (ConversionException e) {
                e.printStackTrace();
            }
        }
        if (pathRowFilter != null) {
            SearchFilterOr orFilter = new SearchFilterOr();
            for (String pathRow : pathRowFilter) {
                SearchFilterAnd andFilter = new SearchFilterAnd();
                filter = new SearchFilterValue();
                filter.setFieldId(fieldIds.get("WRS Path"));
                filter.setOperand("=");
                filter.setValue(pathRow.substring(0, 3));
                andFilter.addChildFilter(filter);
                filter = new SearchFilterValue();
                filter.setFieldId(fieldIds.get("WRS Row"));
                filter.setOperand("=");
                filter.setValue(pathRow.substring(3, 6));
                andFilter.addChildFilter(filter);
                orFilter.addChildFilter(andFilter);
            }
            request.withFilter(orFilter);
        }
        if (pgNumber > 0 && pgSize > 0) {
            request.startingAtIndex((pgNumber - 1) * pgSize + 1);
            request.withMaxResults(pgSize);
        } else {
            request.withMaxResults(Math.max(this.limit, pgSize));
        }
        return buildGetRequestURL("search", request);
    }

    private Object executeQuery(int start, int pageSize, Set<String> pathRows, boolean count) throws Exception {
        final Object results;
        if (!count) {
            results = new ArrayList<EOProduct>();
        } else {
            results = new AtomicLong(0);
        }
        if (pathRows != null && pathRows.size() > 0) {
            final Set<String> single = new HashSet<>();
            pathRows.forEach(pr -> {
                try {
                    single.clear();
                    single.add(pr);
                    String queryUrl = buildQueryUrl(start, pageSize, single);
                    logger.fine(String.format("Executing query for product : %s", queryUrl));
                    try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, null)) {
                        switch (response.getStatusLine().getStatusCode()) {
                            case 200:
                                String body = EntityUtils.toString(response.getEntity());
                                ResponseParser parser = new JsonResponseParser<>(new SearchResponseHandler(), "totalHits");
                                if (!count) {
                                    ((List<EOProduct>) results).addAll(parser.parse(body));
                                } else {
                                    ((AtomicLong) results).addAndGet(parser.parseCount(body));
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
                } catch (Exception e) {
                    throw new QueryException(e);
                }
            });
        } else {
            String queryUrl = buildQueryUrl(start, pageSize, pathRows);
            logger.fine("Executing query " + queryUrl);
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, null)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        String body = EntityUtils.toString(response.getEntity());
                        ResponseParser parser = new JsonResponseParser<>(new SearchResponseHandler(), "totalHits");
                        if (!count) {
                            ((List<EOProduct>) results).addAll(parser.parse(body));
                        } else {
                            ((AtomicLong) results).addAndGet(parser.parseCount(body));
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
        if (!count) {
            List<EOProduct> trimmed = (List<EOProduct>) results;
            int currentSize = trimmed.size();
            if (currentSize > 0 && this.limit > 0 && currentSize > this.limit) {
                trimmed.subList(this.limit, currentSize).clear();
            }
            if (trimmed.size() > 0) {
                resolveDownloadUrls((List<EOProduct>) results);
            }
        }
        logger.info(String.format("Query returned %s products",
                                  count ? ((AtomicLong) results).get() : ((List<EOProduct>) results).size()));
        return count ? ((AtomicLong) results).get() : results;

    }
}
