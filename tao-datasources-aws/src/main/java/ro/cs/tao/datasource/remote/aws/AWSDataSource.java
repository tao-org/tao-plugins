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
package ro.cs.tao.datasource.remote.aws;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import ro.cs.tao.datasource.remote.URLDataSource;
import ro.cs.tao.datasource.remote.aws.parameters.AWSParameterProvider;
import ro.cs.tao.datasource.util.Logger;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

/**
 * @author Cosmin Cara
 */
public class AWSDataSource extends URLDataSource<AWSDataQuery, String> {
    private static String S2_URL;
    private static String S2_TILES_URL;
    private static String L8_URL;
    private static String S2_REQUEST_PAYER;
    private static String L8_REQUEST_PAYER;

    private String accessKeyId;
    private String secretAccessKey;

    static {
        Properties props = new Properties();
        try {
            props.load(AWSDataSource.class.getResourceAsStream("aws.properties"));
            S2_URL = props.getProperty("s2.aws.search.url");
            S2_TILES_URL = props.getProperty("s2.aws.tiles.url");
            L8_URL = props.getProperty("l8.aws.search.url");
            S2_REQUEST_PAYER = props.getProperty("s2.aws." + S3AuthenticationV4.REQUEST_PAYER_HEADER_NAME);
            L8_REQUEST_PAYER = props.getProperty("l8.aws." + S3AuthenticationV4.REQUEST_PAYER_HEADER_NAME);
        } catch (IOException ignored) {
        }
    }

    public AWSDataSource() throws URISyntaxException {
        super(S2_URL);
        setParameterProvider(new AWSParameterProvider(this));
    }

    @Override
    public boolean requiresAuthentication() { return true; }

    public HttpURLConnection buildS3Connection(HttpMethod method, String urlString) throws MalformedURLException {
        URL url = new URL(urlString);
        String region = S3AuthenticationV4.fetchRegionName(url);
        List<NameValuePair> customParameters = new ArrayList<>();
        String requestPayer = AWSDataSource.getRequestPayer(remoteUrl.toURL());
        if (requestPayer != null && !requestPayer.isEmpty()) {
            customParameters.add(new BasicNameValuePair(S3AuthenticationV4.REQUEST_PAYER_HEADER_NAME, requestPayer));
        }
        S3AuthenticationV4 s3AuthenticationV4 = new S3AuthenticationV4(method.name(), region, accessKeyId, secretAccessKey, customParameters);
        return NetUtils.openConnection(method, urlString, s3AuthenticationV4.getAuthorizationToken(url), s3AuthenticationV4.getAwsHeaders(url));
    }

    String getS3ResponseAsString(HttpMethod method, String urlString) throws IOException {
        String result = null;
        HttpURLConnection connection = buildS3Connection(method, urlString);
        if (connection != null) {
            switch (connection.getResponseCode()) {
                case 200:
                    result = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
                    break;
                case 401:
                    String msg = "The supplied credentials are invalid!";
                    Logger.getRootLogger().warn(msg);
                    throw new IllegalArgumentException(msg);
                default:
                    String reason = new Scanner(connection.getErrorStream(), StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
                    msg = ("The request was not successful. Reason: " + reason);
                    Logger.getRootLogger().warn(msg);
                    throw new IllegalStateException(msg);
            }
            connection.disconnect();
        } else {
            Logger.getRootLogger().warn(String.format("The url %s was not reachable", urlString));
        }
        return result;
    }

    private static String getRequestPayer(URL url) {
        if (S2_URL.contains(url.getHost()) || S2_TILES_URL.contains(url.getHost())) {
            return S2_REQUEST_PAYER;
        }
        if (L8_URL.contains(url.getHost())) {
            return L8_REQUEST_PAYER;
        }
        return null;
    }

    @Override
    public void setCredentials(String username, String password) {
        super.setCredentials(username, password);
        accessKeyId = username;
        secretAccessKey = password;
    }

    @Override
    public String authenticate() throws IOException {
        return null;
    }

    @Override
    public boolean ping() {
        try {
            HttpURLConnection connection = buildS3Connection(HttpMethod.GET, S2_URL);
            return 200 == connection.getResponseCode() || 400 == connection.getResponseCode();
        } catch (Exception e) {
            //nothing
        }
        return false;
    }

    @Override
    public String defaultId() {
        return "Amazon Web Services";
    }

    @Override
    protected AWSDataQuery createQueryImpl(String sensorName) {
        try {
            switch (sensorName) {
                case "Sentinel2":
                    this.connectionString = S2_URL;
                    this.remoteUrl = new URI(this.connectionString);
                    break;
                case "Landsat8":
                    this.connectionString = L8_URL;
                    this.remoteUrl = new URI(this.connectionString);
                    break;
                default:
                    throw new IllegalArgumentException(String.format("'%s' is not supported", sensorName));
            }
            return new AWSDataQuery(this, sensorName);
        } catch (URISyntaxException ex) {
            throw new RuntimeException("Malformed url: " + ex.getMessage());
        }
    }
}
