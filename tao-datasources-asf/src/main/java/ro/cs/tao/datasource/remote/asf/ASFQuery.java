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
package ro.cs.tao.datasource.remote.asf;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.converters.ConversionException;
import ro.cs.tao.datasource.converters.ConverterFactory;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.asf.handlers.AsfJsonResponseHandler;
import ro.cs.tao.datasource.remote.asf.parameters.DateParameterConverter;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * ASF Sentinel1 Query
 *
 * @author Valentin Netoiu
 */
public class ASFQuery extends DataQuery {

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
    private static final ConverterFactory converterFactory = ConverterFactory.getInstance();

    static {
        converterFactory.register(DateParameterConverter.class, Date.class);
    }

    public ASFQuery(ASFDataSource source, String sensorName) {
        super(source, sensorName);
    }

    @Override
    public String defaultId() {
        return "ASFQuery";
    }

    protected List<EOProduct> executeImpl() {
        List<EOProduct> results = new ArrayList<>();
        List<NameValuePair> params = new ArrayList<>();
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
            try {
                params.add(new BasicNameValuePair(getRemoteName(parameter.getName()), converterFactory.create(parameter).stringValue()));
            } catch (ConversionException e) {
                throw new QueryException(e.getMessage());
            }
        }


        List<EOProduct> tmpResults;
        List<NameValuePair> queryParams = new ArrayList<>(params);
        if (this.limit > 0) {
            queryParams.add(new BasicNameValuePair("maxResults", String.valueOf(this.limit)));
        }
        queryParams.add(new BasicNameValuePair("output", "json"));

        String queryUrl = this.source.getConnectionString() + "?" + URLEncodedUtils.format(queryParams, "UTF-8").replace("+", "%20");
        logger.fine(String.format("Executing query %s", queryUrl));
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, this.source.getCredentials())) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    JsonResponseParser<EOProduct> parser = new JsonResponseParser<EOProduct>(new AsfJsonResponseHandler()) {
                        @Override
                        public String[] getExcludedAttributes() {
                            return new String[]{"keywords", "links", "services"};
                        }
                    };
                    tmpResults = parser.parse(EntityUtils.toString(response.getEntity()));
                    if (tmpResults != null) {
                        results.addAll(tmpResults);
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
        if (this.limit > 0 && results.size() > this.limit) {
            logger.info(String.format("Query {%s-%s} returned %s products", this.source.getId(), this.sensorName, this.limit));
            return results.subList(0, this.limit);
        } else {
            logger.info(String.format("Query {%s-%s} returned %s products", this.source.getId(), this.sensorName, results.size()));
            return results;
        }
    }

}
