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

package ro.cs.tao.integration.geostorm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ro.cs.tao.services.interfaces.GeostormService;
import ro.cs.tao.services.model.geostormcatalog.Resource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.util.logging.Logger;


/**
 * Geostorm client used for calling Geostorm services
 *
 * @author Oana H.
 */
@Component
@PropertySource("classpath:geostorm.properties")
public class GeostormClient implements GeostormService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${geostorm.rest.base.url}")
    private String geostormRestBaseURL;

    @Value("${geostorm.rest.catalog.resource.endpoint}")
    private String geostormRestCatalogResourceEndpoint;

    @Value("${geostorm.admin.username}")
    private String geostormUsername;

    @Value("${geostorm.admin.password}")
    private String geostormPassword;

    private static final Logger logger = Logger.getLogger(GeostormClient.class.getName());

    public String getResources() {
        trustSelfSignedSSL();
        ResponseEntity<String> result;
        final HttpHeaders headers = createHeaders(geostormUsername, geostormPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);
        final HttpEntity<String> httpEntity = new HttpEntity<String>(headers);
        final String url = geostormRestBaseURL + geostormRestCatalogResourceEndpoint;
        logger.fine("URL = "+ url);
        result = restTemplate.exchange(url, HttpMethod.GET, httpEntity, String.class);
        try {
            logger.info("getResources result: " + new ObjectMapper().writeValueAsString(result));
        } catch (JsonProcessingException e) {
            logger.severe(String.format("getResources(): JSON exception '%s'", e.getMessage()));
        }
        return result.getBody();
    }

    @Override
    public String addResource(Resource resource) {
        trustSelfSignedSSL();
        ResponseEntity<String> result;
        final HttpHeaders headers = createHeaders(geostormUsername, geostormPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);
        final HttpEntity<Resource> httpEntity = new HttpEntity<Resource>(resource, headers);
        final String url = geostormRestBaseURL + geostormRestCatalogResourceEndpoint;
        logger.fine("URL = "+ url);
        logger.fine("Headers = " + headers.toString());
        try {
            logger.fine("Body = " + new ObjectMapper().writeValueAsString(httpEntity));
        } catch (JsonProcessingException e) {
            logger.severe(String.format("addResource(): Body JSON exception '%s'", e.getMessage()));
        }
        result = restTemplate.postForEntity(url, httpEntity, String.class );
        try {
            logger.fine("addResource result:" + new ObjectMapper().writeValueAsString(result));
        } catch (JsonProcessingException e) {
            logger.severe(String.format("addResource(): Result JSON exception '%s'", e.getMessage()));
        }
        return result.getBody();
    }

    private void prepareSSL() {
        System.setProperty("javax.net.ssl.trustStore", "*");
        System.setProperty("javax.net.ssl.trustStorePassword", "");
  }

    public static void trustSelfSignedSSL() {
        logger.entering(GeostormClient.class.getSimpleName(), "trustSelfSignedSSL");
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            X509TrustManager tm = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] xcs, String string) {
                }
                @Override
                public void checkServerTrusted(X509Certificate[] xcs, String string) {
                }
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };
            ctx.init(null, new TrustManager[] { tm }, null);
            SSLContext.setDefault(ctx);
        } catch (Exception ex) {
            logger.severe(ex.getMessage());
        }
    }

    HttpHeaders createHeaders(String username, String password) {
        return new HttpHeaders() {{
            String auth = username + ":" + password;
            byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("US-ASCII")));
            String authHeader = "Basic " + new String(encodedAuth);
            set("Authorization", authHeader );
        }};
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public String getGeostormRestBaseUrl(){
        return geostormRestBaseURL;
    }

    public String getGeostormRestCatalogResourceEndpoint(){
        return geostormRestCatalogResourceEndpoint;
    }

}
