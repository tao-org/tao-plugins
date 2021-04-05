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

package ro.cs.tao.datasource.remote.eocat;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.eocat.auth.EOCATAuthentication;
import ro.cs.tao.datasource.remote.eocat.parameters.EOCATParameterProvider;
import ro.cs.tao.datasource.remote.eocat.xml.EOCATCollectionXmlResponseHandler;
import ro.cs.tao.datasource.remote.result.xml.XmlResponseParser;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class EOCATDataSource extends URLDataSource<EOCATDataQuery, Header> {

    private static final Map<String, Map<String, DataSourceParameter>> SUPPORTED_PARAMETERS = new HashMap<>();
    private static String URL;

    static {
        Properties props = new Properties();
        try {
            props.load(EOCATDataSource.class.getResourceAsStream("eocat.properties"));
            URL = props.getProperty("eocat.search.url");
            if (!URL.endsWith("/")) {
                URL += "/";
            }
            new Thread(EOCATDataSource::computeSupportedParameters).start();
        } catch (IOException ignored) {
        }
    }

    private EOCATAuthentication authenticationStrategy = null;

    public EOCATDataSource() throws URISyntaxException {
        super(URL);
        setParameterProvider(new EOCATParameterProvider(this, URL + "description.xml"));
    }

    private static void computeSupportedParameters() {
        ServiceRegistry<DataSource> serviceRegistry = ServiceRegistryManager.getInstance().getServiceRegistry(DataSource.class);
        EOCATDataSource fedEODataSource = (EOCATDataSource) serviceRegistry.getService(EOCATDataSource.class);
        Map<String, Map<String, DataSourceParameter>> supportedParams = fedEODataSource.getSupportedParams();
        SUPPORTED_PARAMETERS.putAll(supportedParams);
    }

    @Override
    public String defaultId() {
        return "EO-CAT";
    }

    @Override
    public boolean ping() {
        return NetUtils.isAvailable(URL, "", "");
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public Header authenticate() throws IOException {
        if (credentials == null) {
            throw new IOException("No credentials set");
        }
        if (authenticationStrategy == null) {
            authenticationStrategy = new EOCATAuthentication(this.credentials);
        }
        return new BasicHeader(authenticationStrategy.getAuthenticationTokenName(), authenticationStrategy.getAuthenticationTokenValue());
    }

    @Override
    protected EOCATDataQuery createQueryImpl(String sensorName) {
        return new EOCATDataQuery(this, sensorName);
    }

    private String[] fetchCollections(String platform) {
        List<String> results;
        List<NameValuePair> queryParams = new ArrayList<>();
        queryParams.add(new BasicNameValuePair("platform", String.valueOf(platform)));
        queryParams.add(new BasicNameValuePair("maximumRecords", String.valueOf("50")));
        String queryUrl = this.getConnectionString() + "request?" + URLEncodedUtils.format(queryParams, "UTF-8").replace("+", "%20");
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, queryUrl, null)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    XmlResponseParser<String> parser = new XmlResponseParser<>();
                    parser.setHandler(new EOCATCollectionXmlResponseHandler("entry"));
                    results = parser.parse(EntityUtils.toString(response.getEntity()));
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
        return results.toArray(new String[0]);
    }

    @Override
    public Map<String, Map<String, DataSourceParameter>> getSupportedParameters() {
        return SUPPORTED_PARAMETERS;
    }

    private Map<String, Map<String, DataSourceParameter>> getSupportedParams() {
        Map<String, Map<String, DataSourceParameter>> supportedParams = super.getSupportedParameters();
        for (Map.Entry<String, Map<String, DataSourceParameter>> supportedParam : supportedParams.entrySet()) {
            DataSourceParameter currentParentIdentifiser = supportedParam.getValue().get("parentIdentifier");
            Object[] collections = fetchCollections(supportedParam.getKey());
            DataSourceParameter newParentIdentifiser = new DataSourceParameter(currentParentIdentifiser.getName(), currentParentIdentifiser.getRemoteName(), currentParentIdentifiser.getType(), currentParentIdentifiser.getLabel(), currentParentIdentifiser.getDefaultValue(), true, collections);
            supportedParam.getValue().replace("parentIdentifier", newParentIdentifiser);
        }
        return supportedParams;
    }
}
