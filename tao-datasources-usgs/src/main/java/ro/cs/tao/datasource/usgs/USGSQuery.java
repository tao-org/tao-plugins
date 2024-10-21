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
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class USGSQuery extends DataQuery {

    private static final Map<String, String> fieldIds;
    private static final String C2_LEVEL1_COLLECTION = "landsat_ot_c2_l1";
    private static final String SATELLITE_PARAM_REMOTE_NAME = "Satellite";
    private static final String SATELLITE_PARAM_ALT_REMOTE_NAME = "Spacecraft Identifier";
    private static final Set<String> formats = new HashSet<>() {{
        add("geotiff");
        add("hdf");
        add("netcdf");
    }};
    private String apiKey;

    static {
        ConverterFactory factory = new ConverterFactory();
        factory.register(USGSDateParameterConverter.class, LocalDateTime.class);
        converterFactory.put(USGSQuery.class, factory);
        fieldIds = new HashMap<>();
    }

    USGSQuery(USGSDataSource source, String mission) {
        super(source, mission);
    }

    @Override
    public String defaultId() {
        return "USGSLandsat8Query";
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        try {
            return executeQuery(this.pageNumber, this.pageSize).getProducts();
        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    @Override
    protected long getCountImpl() {
        try {
            return executeQuery(1, 1).getCount();
        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    private void setupRemoteFields() {
        fieldIds.clear();
        DatasetFieldsRequest request = new DatasetFieldsRequest();
        request.setDatasetName(this.parameters.containsKey(CommonParameterNames.PLATFORM) ?
                this.parameters.get(CommonParameterNames.PLATFORM).getValueAsString() :
                C2_LEVEL1_COLLECTION);
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

    private List<DownloadInfo> getDownloadInfos(List<EOProduct> products) {
        DownloadOptions optionsRequest = new DownloadOptions();
        optionsRequest.setDatasetName(this.parameters.containsKey(CommonParameterNames.PLATFORM) ?
                this.parameters.get(CommonParameterNames.PLATFORM).getValueAsString() :
                                              C2_LEVEL1_COLLECTION);
        int size = products.size();
        String[] ids = new String[size];
        for (int i = 0; i < size; i++) {
            ids[i] = products.get(i).getId();
        }
        optionsRequest.setEntityIds(String.join(",", ids));
        String url = buildPostRequestURL("download-options");
        final List<DownloadInfo> downloadInfos;
        try (CloseableHttpResponse response =
                     NetUtils.openConnection(HttpMethod.POST, url, "X-Auth-Token", this.apiKey, optionsRequest.toString())) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(response.getEntity());
                    JsonResponseParser<DownloadInfo> parser = new JsonResponseParser<>(new DownloadInfoResponseHandler());
                    downloadInfos = parser.parse(body);
                    if (downloadInfos == null) {
                        return null;
                    }
                    downloadInfos.removeIf(i -> (!i.getDownloadSystem().toLowerCase().contains("zip") && !i.getDownloadSystem().toLowerCase().contains("dds")) || i.getProductName().toLowerCase().contains("metadata"));
                    if (this.sensorName.equalsIgnoreCase("Hyperion")) {
                        downloadInfos.removeIf(i -> formats.stream().noneMatch(f -> i.getProductName().toLowerCase().contains(f)));
                    }
                    // It may happen to have two entries for the same entityId, one available and one not.
                    // Then, in order to properly populate the 'notAvailable' set, we need to sort the list by
                    // entityId and then by 'available' (if y/n, n comes before y, if true/false, false comes before true)
                    downloadInfos.sort(Comparator.comparing(DownloadInfo::getEntityId).thenComparing(i -> i.getDownloadSystem().toLowerCase().contains("dds")));
                    downloadInfos.sort(Comparator.comparing(DownloadInfo::getEntityId).thenComparing(DownloadInfo::getAvailable));
                    for (DownloadInfo info : downloadInfos) {
                        if (!("y".equalsIgnoreCase(info.getAvailable()) || "true".equalsIgnoreCase(info.getAvailable()))) {
                            logger.warning(String.format("Product %s is not available for download system %s", info.getDisplayId(), info.getDownloadSystem()));
                        }
                        products.stream().filter(p -> p.getName().equals(info.getDisplayId())).findFirst().ifPresent(product -> product.setApproximateSize(info.getFilesize()));
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
        // deduplicate if multiple occurences of the same product
        return new ArrayList<>(downloadInfos.stream().collect(Collectors.toMap(DownloadInfo::getEntityId, Function.identity(), (e1, e2) -> e2, LinkedHashMap::new)).values());
        //return downloadInfos;
    }

    private void fillDownloadUrls(List<EOProduct> products, List<DownloadInfo> downloadInfos) {
        if (downloadInfos != null) {
            final DownloadRequest downloadRequest = new DownloadRequest();
            downloadRequest.setDownloads(downloadInfos.stream()
                    .map(i -> new Download() {{
                        setEntityId(i.getEntityId());
                        setProductId(i.getId());
                    }})
                    .collect(Collectors.toList()));
            String url = buildPostRequestURL("download-request");
            List<AvailableDownload> downloads = null;
            try (CloseableHttpResponse lastOne =
                         NetUtils.openConnection(HttpMethod.POST, url, "X-Auth-Token", this.apiKey, downloadRequest.toString())) {
                switch (lastOne.getStatusLine().getStatusCode()) {
                    case 200:
                        String body = EntityUtils.toString(lastOne.getEntity());
                        JsonResponseParser<DownloadResponse> parser = new JsonResponseParser<>(new DownloadResponseHandler());
                        DownloadResponse resp = parser.parseValue(body);
                        if (resp != null) {
                            downloads = resp.getData().getAvailableDownloads();
                            downloads.addAll(resp.getData().getPreparingDownloads());
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
            if (downloads != null) {
                if (products.size() != downloads.size()) {
                    logger.severe("Retrieved less download URLs than requested products");
                } else {
                    for (int i = 0; i < products.size(); i++) {
                        try {
                            EOProduct current = products.get(i);
                            AvailableDownload download = downloads.stream().filter(d -> d.getUrl().contains(current.getName())).findFirst().orElse(null);
                            // Try to match the old way (product name included in query string), then fill by position
                            current.setLocation(download != null ? download.getUrl() : downloads.get(i).getUrl());
                        } catch (URISyntaxException e) {
                            logger.warning(e.getMessage());
                        }
                    }
                }
            }
        }
    }

    private String buildPostRequestURL(String operation) {
        return this.source.getConnectionString() + operation;
    }

    private SearchRequest buildQuery(int pgNumber, int pgSize) {
        if (!this.parameters.containsKey(CommonParameterNames.PLATFORM)) {
            addParameter(CommonParameterNames.PLATFORM, this.dataSourceParameters.get(CommonParameterNames.PLATFORM).getDefaultValue());
        }
        SearchRequest request = new SearchRequest()
                .withDataSet(this.parameters.containsKey(CommonParameterNames.PLATFORM)
                             ? this.parameters.get(CommonParameterNames.PLATFORM).getValueAsString()
                             : C2_LEVEL1_COLLECTION);
        SearchFilterValue filter;
        Map<String, QueryParameter<?>> parameters = new HashMap<>(this.parameters);
        for (QueryParameter<?> parameter : parameters.values()) {
            try {
                final String remoteName = getRemoteName(parameter.getName());
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
                    case "minClouds":
                        request.withMinClouds(parameter.getValueAsInt());
                        break;
                    case "maxClouds":
                        request.withMaxClouds(parameter.getValueAsInt());
                        break;
                    case CommonParameterNames.TILE:
                        final String rowParameterRemoteName = getRemoteName("row");
                        final String pathParameterRemoteName = getRemoteName("path");
                        if (fieldIds.containsKey(rowParameterRemoteName) && fieldIds.containsKey(pathParameterRemoteName)) {
                            String pathRow = parameters.get(CommonParameterNames.TILE).getValueAsString();
                            filter = new SearchFilterValue();
                            filter.setFilterId(fieldIds.get(rowParameterRemoteName));
                            filter.setOperand("=");
                            filter.setValue(pathRow.substring(0, 3));
                            request.withFilter(filter);
                            filter = new SearchFilterValue();
                            filter.setFilterId(fieldIds.get(pathParameterRemoteName));
                            filter.setOperand("=");
                            filter.setValue(pathRow.substring(3, 6));
                            request.withFilter(filter);
                        }
                        break;
                    default:
                        if (fieldIds.containsKey(remoteName)) {
                            filter = new SearchFilterValue();
                            filter.setFilterId(fieldIds.get(remoteName));
                            filter.setOperand("=");
                            filter.setValue(parameter.getValueAsString());
                            request.withFilter(filter);
                        }
                }
            } catch (ConversionException e) {
                logger.severe("Build query ERROR: " + e.getMessage());
            }
        }
        if (this.sensorName.toLowerCase().startsWith("landsat")) {
            final String remoteName;
            final String value;
            if (fieldIds.containsKey(SATELLITE_PARAM_REMOTE_NAME)) {
                remoteName = SATELLITE_PARAM_REMOTE_NAME;
                value = this.sensorName.substring(this.sensorName.length() - 1);
            } else {
                remoteName = SATELLITE_PARAM_ALT_REMOTE_NAME;
                value = this.sensorName.substring(0, this.sensorName.length() - 1).toUpperCase() + "_" + this.sensorName.substring(this.sensorName.length() - 1);
            }
            if (fieldIds.containsKey(remoteName)) {
                filter = new SearchFilterValue();
                filter.setFilterId(fieldIds.get(remoteName));
                filter.setOperand("=");
                filter.setValue(value);
                request.withFilter(filter);
            }
        }
        if (pgNumber > 0 && pgSize > 0) {
            request.startingAtIndex((pgNumber - 1) * pgSize + 1);
            request.withMaxResults(pgSize);
        } else {
            request.withMaxResults(Math.max(Math.max(this.limit, pgSize), 1));
        }
        return request;
    }

    private QueryResult executeQuery(int start, int pageSize) {
        if (this.apiKey == null) {
            this.apiKey = ((USGSDataSource) this.source).authenticate();
        }
        setupRemoteFields();
        final List<EOProduct> products = new ArrayList<>();
        final AtomicLong count = new AtomicLong(0);
        final SearchRequest request = buildQuery(start, pageSize);
        final String queryUrl = buildPostRequestURL("scene-search");
        logger.finest("Executing query " + queryUrl + " with payload " + request);
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, queryUrl, "X-Auth-Token", apiKey, request.toString())) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    final String body = EntityUtils.toString(response.getEntity());
                    if (body.contains("SERVER_ERROR")) {
                        throw new QueryException("The request was not successful. Reason: API Server Error");
                    }
                    final ResponseParser<EOProduct> parser = new JsonResponseParser<>(new SearchResponseHandler(this.sensorName ,this.source.getSensorTypes().get(this.sensorName).getSensorType(), this.coverageFilter),
                            "totalHits");
                    final List<EOProduct> parsedProducts = parser.parse(body);
                    if (parsedProducts != null) {
                        products.addAll(parsedProducts);
                    }
                    count.addAndGet(parser.parseCount(body));
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
        final int currentSize = products.size();
        if (currentSize > 0 && this.limit > 0 && currentSize > this.limit) {
            products.subList(this.limit, currentSize).clear();
        }
        boolean counting = pageSize < 2;
        if (!products.isEmpty() && !counting) {
            final List<DownloadInfo> downloadInfos = getDownloadInfos(products);
            if (downloadInfos != null && !downloadInfos.isEmpty()) {
                fillDownloadUrls(products, downloadInfos);
            }
        }
        logger.info(String.format("Query returned %s products", products.size()));
        return new QueryResult(products, count.get());
    }

    private static class QueryResult {
        private final List<EOProduct> products;
        private final long count;

        public QueryResult(List<EOProduct> products, long count) {
            this.products = products;
            this.count = count;
        }

        public List<EOProduct> getProducts() {
            return products;
        }

        public long getCount() {
            return count;
        }
    }
}
