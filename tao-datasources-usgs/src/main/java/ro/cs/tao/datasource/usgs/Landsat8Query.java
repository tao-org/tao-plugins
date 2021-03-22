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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.result.ResponseParser;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.datasource.usgs.json.handlers.DatasetFieldsResponseHandler;
import ro.cs.tao.datasource.usgs.json.handlers.DownloadInfoResponseHandler;
import ro.cs.tao.datasource.usgs.json.handlers.DownloadResponseHandler;
import ro.cs.tao.datasource.usgs.json.handlers.SearchResponseHandler;
import ro.cs.tao.datasource.usgs.json.requests.*;
import ro.cs.tao.datasource.usgs.json.responses.AvailableDownload;
import ro.cs.tao.datasource.usgs.json.responses.DownloadInfo;
import ro.cs.tao.datasource.usgs.json.responses.DownloadResponse;
import ro.cs.tao.datasource.usgs.json.types.Download;
import ro.cs.tao.datasource.usgs.parameters.USGSDateParameterConverter;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.products.landsat.Landsat8TileExtent;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;
import ro.cs.tao.utils.StringUtilities;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class Landsat8Query extends DataQuery {

    private static final Map<String, String> fieldIds;
    private static final String LEVEL1_COLLECTION = "LANDSAT_8_C1";
    private static final String LEVEL2_COLLECTION = "LANDSAT_OT_C2_L2";
    private String apiKey;

    static {
        ConverterFactory factory = new ConverterFactory();
        factory.register(USGSDateParameterConverter.class, Date.class);
        converterFactory.put(Landsat8Query.class, factory);
        fieldIds = new HashMap<>();
    }

    Landsat8Query(USGSDataSource source, String mission) {
        super(source, mission);
    }

    @Override
    public String defaultId() {
        return "USGSLandsat8Query";
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        //Set<String> pathRows = getPathRows();
        try {
            return (List<EOProduct>) executeQuery(this.pageNumber, this.pageSize, null, false);
        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    @Override
    protected long getCountImpl() {
        long retVal = 0;
        //Set<String> pathRows = getPathRows();
        try {
            retVal = (long) executeQuery(1, 1, null, true);
        } catch (Exception e) {
            throw new QueryException(e);
        }
        return retVal;
    }

    private Set<String> getPathRows() {
        Set<String> pathRows = null;
        for (QueryParameter<?> parameter : this.parameters.values()) {
            final Class<?> parameterType = parameter.getType();
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

    private void setupRemoteFields() throws Exception {
        if (fieldIds.size() == 0) {
            DatasetFieldsRequest request = new DatasetFieldsRequest();
            request.setDatasetName(this.parameters.containsKey(CommonParameterNames.PLATFORM) ?
                                           this.parameters.get(CommonParameterNames.PLATFORM).getValueAsString() :
                                           LEVEL1_COLLECTION);
            String url = buildPostRequestURL("dataset-filters");
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, url, "X-Auth-Token", this.apiKey, request.toString())) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        String body = EntityUtils.toString(response.getEntity());
                        JsonResponseParser<FieldDescriptor> parser = new JsonResponseParser<>(new DatasetFieldsResponseHandler());
                        List<FieldDescriptor> params = parser.parse(body);
                        for (FieldDescriptor param : params) {
                            fieldIds.put(param.getFieldLabel(), param.getId());
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

    private List<DownloadInfo> getDownloadInfos(List<EOProduct> products) {
        DownloadOptions optionsRequest = new DownloadOptions();
        optionsRequest.setDatasetName(this.parameters.containsKey(CommonParameterNames.PLATFORM) ?
                this.parameters.get(CommonParameterNames.PLATFORM).getValueAsString() :
                LEVEL1_COLLECTION);
        int size = products.size();
        String[] ids = new String[size];
        for (int i = 0; i < size; i++) {
            ids[i] = products.get(i).getId();
        }
        optionsRequest.setEntityIds(String.join(",", ids));
        String url = buildPostRequestURL("download-options");
        List<DownloadInfo> downloadInfos = null;
        try (CloseableHttpResponse response =
                     NetUtils.openConnection(HttpMethod.POST, url, "X-Auth-Token", this.apiKey, optionsRequest.toString())) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(response.getEntity());
                    JsonResponseParser<DownloadInfo> parser = new JsonResponseParser<>(new DownloadInfoResponseHandler());
                    downloadInfos = parser.parse(body);
                    downloadInfos.removeIf(i ->
                            (optionsRequest.getDatasetName().equals(LEVEL1_COLLECTION) && !i.getProductName().endsWith("Data Product"))
                        || (optionsRequest.getDatasetName().equals(LEVEL2_COLLECTION) && !i.getProductName().endsWith("Product Bundle"))
                        || (i.getProductName().toLowerCase().contains("metadata")));
                    final Iterator<DownloadInfo> iterator = downloadInfos.iterator();
                    final Set<String> notAvailable = new HashSet<>();
                    while (iterator.hasNext()) {
                        DownloadInfo info = iterator.next();
                        if (!("y".equalsIgnoreCase(info.getAvailable()) || "true".equalsIgnoreCase(info.getAvailable()))) {
                            notAvailable.add(info.getEntityId());
                            logger.warning(String.format("Product %s is not available for download system %s",
                                                         info.getDisplayId(), info.getDownloadSystem()));
                            iterator.remove();
                        } else {
                            // it may happen to find duplicates
                            notAvailable.remove(info.getEntityId());
                        }
                        products.stream().filter(p -> p.getName().equals(info.getDisplayId())).findFirst().get().setApproximateSize(info.getFilesize());
                    }
                    products.removeIf(p -> notAvailable.contains(p.getId()));
                    downloadInfos.removeIf(d -> notAvailable.contains(d.getEntityId()));
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
        return downloadInfos;
    }

    private void fillDownloadUrls(List<EOProduct> products, List<DownloadInfo> downloadInfos) {
        final DownloadRequest downloadRequest = new DownloadRequest();
        downloadRequest.setDownloads(downloadInfos.stream()
                .map(i -> new Download() {{ setEntityId(i.getEntityId()); setProductId(i.getId()); }})
                .collect(Collectors.toList()));
        String url = buildPostRequestURL("download-request");
        try (CloseableHttpResponse lastOne =
                     NetUtils.openConnection(HttpMethod.POST, url, "X-Auth-Token", this.apiKey, downloadRequest.toString())) {
            switch (lastOne.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(lastOne.getEntity());
                    JsonResponseParser<DownloadResponse> parser = new JsonResponseParser<>(new DownloadResponseHandler());
                    DownloadResponse resp = parser.parseValue(body);
                    if (resp != null) {
                        List<AvailableDownload> downloads = resp.getData().getAvailableDownloads();
                        if (downloads == null || downloads.size() == 0) {
                            downloads = resp.getData().getPreparingDownloads();
                        }
                        if (downloads != null) {
                            for (int i = 0; i < downloads.size(); i++) {
                                products.get(i).setLocation(downloads.get(i).getUrl());
                            }
                        }
                    }
                    break;
                case 401:
                    throw new QueryException("The supplied credentials are invalid!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                            lastOne.getStatusLine().getReasonPhrase()));
            }
        } catch (Exception ex) {
            throw new QueryException(ex);
        }
    }

    private String buildPostRequestURL(String operation) {
        return this.source.getConnectionString() + operation;
    }

    private SearchRequest buildQuery(int pgNumber, int pgSize, Set<String> pathRowFilter) throws Exception {
        if (!this.parameters.containsKey(CommonParameterNames.PLATFORM)) {
            addParameter(CommonParameterNames.PLATFORM, this.dataSourceParameters.get(CommonParameterNames.PLATFORM).getDefaultValue());
        }
        SearchRequest request = new SearchRequest().withDataSet(this.parameters.containsKey(CommonParameterNames.PLATFORM) ?
                this.parameters.get(CommonParameterNames.PLATFORM).getValueAsString() :
                LEVEL1_COLLECTION);
        SearchFilterValue filter;
        Map<String, QueryParameter> parameters = new HashMap<>(this.parameters);
        if ((parameters.containsKey("row") && parameters.containsKey("path")) ||
             parameters.containsKey(CommonParameterNames.TILE)) {
            parameters.remove(CommonParameterNames.FOOTPRINT);
        }
        for (QueryParameter<?> parameter : parameters.values()) {
            try {
                switch (parameter.getName()) {
                    case CommonParameterNames.PLATFORM:
                        request.withDataSet(parameter.getValueAsString());
                        break;
                    case CommonParameterNames.START_DATE:
                        request.withStartDate(getParameterValue(parameter));
                        break;
                    case CommonParameterNames.END_DATE:
                        request.withEndDate(getParameterValue(parameter));
                        break;
                    case CommonParameterNames.FOOTPRINT:
                        Polygon2D footprint = (Polygon2D) parameter.getValue();
                        Rectangle2D bounds = footprint.getBounds2D();
                        request.withLowerLeft(bounds.getMinX(), bounds.getMinY())
                               .withUpperRight(bounds.getMaxX(), bounds.getMaxY());
                        break;
                    case CommonParameterNames.PRODUCT:
                        filter = new SearchFilterValue();
                        filter.setFilterId(fieldIds.get("Landsat Product Identifier"));
                        filter.setOperand("=");
                        filter.setValue(parameter.getValueAsString());
                        request.withFilter(filter);
                        break;
                    case CommonParameterNames.PRODUCT_TYPE:
                        if (!LEVEL2_COLLECTION.equals(request.getDatasetName())) {
                            filter = new SearchFilterValue();
                            filter.setFilterId(fieldIds.get("Data Type Level-1"));
                            filter.setOperand("=");
                            filter.setValue(parameter.getValueAsString());
                            request.withFilter(filter);
                        }
                        break;
                    case "minClouds":
                        request.withMinClouds(parameter.getValueAsInt());
                        break;
                    case "maxClouds":
                        request.withMaxClouds(parameter.getValueAsInt());
                        break;
                    case "row":
                        filter = new SearchFilterValue();
                        filter.setFilterId(fieldIds.get("WRS Row"));
                        filter.setOperand("=");
                        filter.setValue(parameter.getValueAsString());
                        request.withFilter(filter);
                        break;
                    case "path":
                        filter = new SearchFilterValue();
                        filter.setFilterId(fieldIds.get("WRS Path"));
                        filter.setOperand("=");
                        filter.setValue(parameter.getValueAsString());
                        request.withFilter(filter);
                        break;
                    case CommonParameterNames.TILE:
                        String pathRow = parameters.get(CommonParameterNames.TILE).getValueAsString();
                        filter = new SearchFilterValue();
                        filter.setFilterId(fieldIds.get("WRS Path"));
                        filter.setOperand("=");
                        filter.setValue(pathRow.substring(0, 3));
                        request.withFilter(filter);
                        filter = new SearchFilterValue();
                        filter.setFilterId(fieldIds.get("WRS Row"));
                        filter.setOperand("=");
                        filter.setValue(pathRow.substring(3, 6));
                        request.withFilter(filter);
                        break;
                }
            } catch (ConversionException e) {
                e.printStackTrace();
            }
        }
        if (pathRowFilter != null && pathRowFilter.size() > 0) {
            SearchFilterOr orFilter = new SearchFilterOr();
            for (String pathRow : pathRowFilter) {
                SearchFilterAnd andFilter = new SearchFilterAnd();
                filter = new SearchFilterValue();
                filter.setFilterId(fieldIds.get("WRS Path"));
                filter.setOperand("=");
                filter.setValue(pathRow.substring(0, 3));
                andFilter.addChildFilter(filter);
                filter = new SearchFilterValue();
                filter.setFilterId(fieldIds.get("WRS Row"));
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
        return request;
    }

    private Object executeQuery(int start, int pageSize, Set<String> pathRows, boolean count) throws Exception {
        if (this.apiKey == null) {
            this.apiKey = ((USGSDataSource) this.source).authenticate();
            setupRemoteFields();
        }
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
                    final SearchRequest request = buildQuery(start, pageSize, single);
                    String queryUrl = buildPostRequestURL("scene-search");
                    logger.finest(String.format("Executing query for product : %s", request.toString()));
                    try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, queryUrl, "X-Auth-Token", apiKey, request.toString())) {
                        switch (response.getStatusLine().getStatusCode()) {
                            case 200:
                                String body = EntityUtils.toString(response.getEntity());
                                ResponseParser<EOProduct> parser = new JsonResponseParser<>(new SearchResponseHandler(), "totalHits");
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
                    sleep();
                } catch (Exception e) {
                    throw new QueryException(e);
                }
            });
        } else {
            SearchRequest request = buildQuery(start, pageSize, pathRows);
            String queryUrl = buildPostRequestURL("scene-search");
            logger.finest("Executing query " + queryUrl + " with payload " + request.toString());
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, queryUrl, "X-Auth-Token", apiKey, request.toString())) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        String body = EntityUtils.toString(response.getEntity());
                        if (body.contains("SERVER_ERROR")) {
                            throw new QueryException("The request was not successful. Reason: API Server Error");
                        }
                        ResponseParser<EOProduct> parser = new JsonResponseParser<>(new SearchResponseHandler(), "totalHits");
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
                sleep();
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
                fillDownloadUrls(trimmed, getDownloadInfos(trimmed));
            }
        }
        logger.info(String.format("Query returned %s products",
                                  count ? ((AtomicLong) results).get() : ((List<EOProduct>) results).size()));
        return count ? ((AtomicLong) results).get() : results;

    }
}
