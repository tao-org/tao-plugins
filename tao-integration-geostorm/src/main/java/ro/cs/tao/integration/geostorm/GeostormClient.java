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
import org.geotools.geojson.geom.GeometryJSON;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.eodata.EODataHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.integration.geostorm.model.Resource;
import ro.cs.tao.serialization.GeometryAdapter;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.logging.Logger;


/**
 * Geostorm client used for calling Geostorm services
 *
 * @author Oana H.
 */
@Component
@PropertySource("classpath:geostorm.properties")
public class GeostormClient implements EODataHandler<EOProduct> {

    private static final Logger logger = Logger.getLogger(GeostormClient.class.getName());
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    private RestTemplate restTemplate;
    private String geostormRestBaseURL;
    private String geostormRestCatalogResourceEndpoint;
    private String geostormUsername;
    private String geostormPassword;

    public GeostormClient() {
        ConfigurationManager configManager = ConfigurationManager.getInstance();
        geostormRestBaseURL = configManager.getValue("geostorm.rest.base.url");
        geostormRestCatalogResourceEndpoint = configManager.getValue("geostorm.rest.catalog.resource.endpoint");
        geostormUsername = configManager.getValue("geostorm.admin.username");
        geostormPassword = configManager.getValue("geostorm.admin.password");
        if (geostormRestBaseURL == null || geostormRestCatalogResourceEndpoint == null ||
                geostormUsername == null || geostormPassword == null) {
            throw new UnsupportedOperationException("Geostorm integration plugin not configured");
        }
        restTemplate = new RestTemplate();
    }

    @Override
    public Class<EOProduct> isIntendedFor() { return EOProduct.class; }

    @Override
    public int getPriority() { return 1; }

    @Override
    public List<EOProduct> handle(List<EOProduct> list) throws DataHandlingException {

        Resource geostormResource;

        for (EOProduct product: list){
            try {
                // conversion from EOProduct to Resource
                geostormResource = new Resource();
                geostormResource.setExecution_id(0);
                geostormResource.setData_path(product.getLocation());
                geostormResource.setData_type(product.getSensorType() == SensorType.UNKNOWN ? "output" : "input");
                geostormResource.setName(product.getName());
                geostormResource.setManaged_resource_storage(false);
                geostormResource.setShort_description(product.getSensorType().toString() + " " + product.getName());
                String entryPoint = product.getEntryPoint();
                if (entryPoint == null) {
                    entryPoint = product.getLocation();
                }
                geostormResource.setEntry_point(entryPoint);
                geostormResource.setResource_storage_type("process_result");
                geostormResource.setRelease_date(dateFormatter.format(product.getAcquisitionDate()));
                //geostormResource.setCollection(product.getProductType()); // TODO see if there is a predefined collection in Geostorm with this name; if not, define it
                geostormResource.setCollection("Sentinel_2"); // for test, this collection already exists
                geostormResource.setWkb_geometry(convertWKTToGeoJson(product.getGeometry()));
                String crs = product.getCrs();
                if (crs.contains(":")) {
                    crs = crs.substring(crs.indexOf(":") + 1);
                }
                geostormResource.setCoord_sys(new Integer[] { Integer.parseInt(crs) });

                // save resource
                addResource(geostormResource);
            } catch (Exception ex) {
                logger.severe(ex.getMessage());
            }
        }

        return list;
    }

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

    private String convertWKTToGeoJson(String wkt) {
        GeometryJSON geoJson = new GeometryJSON(8);
        GeometryAdapter adapter = new GeometryAdapter();
        try {
            return geoJson.toString(adapter.marshal(wkt));
        } catch (Exception e) {
            logger.warning(e.getMessage());
            return null;
        }
    }

}
