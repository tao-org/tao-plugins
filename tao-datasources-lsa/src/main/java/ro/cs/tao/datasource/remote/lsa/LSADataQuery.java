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

package ro.cs.tao.datasource.remote.lsa;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.lsa.parameters.LSADateParameterConverter;
import ro.cs.tao.datasource.remote.lsa.xml.LSAXmlResponseHandler;
import ro.cs.tao.datasource.remote.result.xml.XmlResponseParser;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LSADataQuery extends DataQuery {

    private static final int MAXIMUM_RECORDS = 50;

    static {
        final ConverterFactory factory = new ConverterFactory();
        factory.register(LSADateParameterConverter.class, LocalDateTime.class);
        converterFactory.put(LSADataQuery.class, factory);
    }

    public LSADataQuery(LSADataSource source, String sensorName) {
        super(source, sensorName);
    }

    @Override
    public String defaultId() {
        return "LSA Data Center Query";
    }


    @Override
    public boolean supportsPaging() {
        return true;
    }


    protected List<EOProduct> executeImpl() {
        final List<EOProduct> results = new ArrayList<>();
        final List<NameValuePair> params = new ArrayList<>();
        for (final Map.Entry<String, QueryParameter<?>> entry : this.parameters.entrySet()) {
            final QueryParameter<?> parameter = entry.getValue();
            if (!parameter.isOptional() && !parameter.isInterval() && parameter.getValue() == null) {
                throw new QueryException(String.format("Parameter [%s] is required but no value is supplied", parameter.getName()));
            }
            if (parameter.isOptional() &&
                    ((!parameter.isInterval() && parameter.getValue() == null) ||
                            (parameter.isInterval() && parameter.getMinValue() == null && parameter.getMaxValue() == null))) {
                continue;
            }
            try {
                params.add(new BasicNameValuePair(getRemoteName(parameter.getName()), getParameterValue(parameter)));
            } catch (ConversionException e) {
                throw new QueryException(e.getMessage());
            }
        }
        if (parameters.containsKey(CommonParameterNames.PRODUCT)) {
            final String remoteName = getRemoteName(CommonParameterNames.PRODUCT);
            params.removeIf(p -> !remoteName.equals(p.getName()));
        }
        List<EOProduct> tmpResults;
        final List<NameValuePair> queryParams = new ArrayList<>(params);
        final long nrProductsFound = this.getCount();
        final int nrRecordsOnPage = Math.max(1, Math.min(this.pageSize, MAXIMUM_RECORDS));
        long remainingProducts = Math.max(1, Math.min(this.pageSize, nrProductsFound));
        queryParams.add(new BasicNameValuePair("count", String.valueOf("" + nrRecordsOnPage)));
        int startRecord = Math.max(1, (this.pageNumber - 1) * nrRecordsOnPage + 1);
        queryParams.add(new BasicNameValuePair("httpAccept", "atom"));
        queryParams.add(new BasicNameValuePair("startIndex", String.valueOf("" + startRecord)));
        do {
            queryParams.remove(queryParams.size() - 1);
            queryParams.add(new BasicNameValuePair("startIndex", String.valueOf("" + startRecord)));
            final String queryUrl = this.source.getConnectionString() + "?" + URLEncodedUtils.format(queryParams, "UTF-8").replace("+", "%20");
            logger.fine(String.format("Executing query %s", queryUrl));
            try (final CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, null)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        final XmlResponseParser<EOProduct> parser = new XmlResponseParser<>();
                        parser.setHandler(new LSAXmlResponseHandler("entry"));
                        tmpResults = parser.parse(EntityUtils.toString(response.getEntity()));
                        if (tmpResults != null) {
                            final String downloadUrl = this.source.getConnectionString("Download");
                            tmpResults.forEach(tr -> {
                                try {
                                    tr.setLocation(downloadUrl + tr.getLocation());
                                } catch (URISyntaxException e) {
                                    logger.warning(e.getMessage());
                                }
                            });
                            results.addAll(tmpResults);
                            startRecord += nrRecordsOnPage;
                            remainingProducts -= nrRecordsOnPage;
                        }
                        break;
                    case 401:
                        throw new QueryException("The request was not successful. Reason: 401: The supplied credentials are invalid!");
                    case 403:
                        throw new QueryException("The request was not successful. Reason: 403: The required credentials are missing!");
                    default:
                        throw new QueryException(String.format("The request was not successful. Reason: %s",
                                response.getStatusLine().getStatusCode() + ": " + response.getStatusLine().getReasonPhrase()));
                }
            } catch (IOException ex) {
                throw new QueryException(ex);
            }
        } while (tmpResults != null && !tmpResults.isEmpty() && remainingProducts > 0);
        if (this.limit > 0 && results.size() > this.limit) {
            logger.info(String.format("Query {%s-%s} returned %s products", this.source.getId(), this.sensorName, this.limit));
            return results.subList(0, this.limit);
        } else {
            logger.info(String.format("Query {%s-%s} returned %s products", this.source.getId(), this.sensorName, results.size()));
            return results;
        }
    }

    @Override
    public long getCountImpl() {
        long count = -1;
        final List<NameValuePair> params = new ArrayList<>();
        for (final Map.Entry<String, QueryParameter<?>> entry : this.parameters.entrySet()) {
            final QueryParameter<?> parameter = entry.getValue();
            if (!parameter.isOptional() && !parameter.isInterval() && parameter.getValue() == null) {
                throw new QueryException(String.format("Parameter [%s] is required but no value is supplied", parameter.getName()));
            }
            if (parameter.isOptional() &&
                    ((!parameter.isInterval() && parameter.getValue() == null) ||
                            (parameter.isInterval() && parameter.getMinValue() == null && parameter.getMaxValue() == null))) {
                continue;
            }
            try {
                params.add(new BasicNameValuePair(getRemoteName(parameter.getName()), getParameterValue(parameter)));
            } catch (ConversionException e) {
                throw new QueryException(e.getMessage());
            }
        }
        final List<NameValuePair> queryParams = new ArrayList<>(params);
        queryParams.add(new BasicNameValuePair("count", "1"));
        queryParams.add(new BasicNameValuePair("httpAccept", "atom"));
        queryParams.add(new BasicNameValuePair("startIndex", "1"));
        final String queryUrl = this.source.getConnectionString() + "?" + URLEncodedUtils.format(queryParams, "UTF-8").replace("+", "%20");
        try (final CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, null)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    final String countRequestResponse = EntityUtils.toString(response.getEntity());
                    final String countText = countRequestResponse.replaceAll("[\\s\\S]*?<os\\:totalResults>([\\d]*?)<\\/os:totalResults>[\\s\\S]*", "$1");
                    if (!countText.isEmpty()) {
                        count = Long.parseLong(countText);
                    }
                    break;
                case 401:
                    throw new QueryException("The request was not successful. Reason: 401: The supplied credentials are invalid!");
                case 403:
                    throw new QueryException("The request was not successful. Reason: 403: The required credentials are missing!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                            response.getStatusLine().getStatusCode() + ": " + response.getStatusLine().getReasonPhrase()));
            }
        } catch (IOException ex) {
            throw new QueryException(ex);
        }
        return count;
    }
}
