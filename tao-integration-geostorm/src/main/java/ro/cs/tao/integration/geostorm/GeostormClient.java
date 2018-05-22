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
import com.jcraft.jsch.*;
import org.apache.commons.codec.binary.Base64;
import org.geotools.geojson.geom.GeometryJSON;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.eodata.EODataHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.integration.geostorm.model.RasterProduct;
import ro.cs.tao.integration.geostorm.model.Resource;
import ro.cs.tao.serialization.GeometryAdapter;
import ro.cs.tao.utils.StringUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
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

    //@Autowired
    //private PersistenceManager persistenceMng;

    private String geostormRestBaseURL;
    private String geostormRestCatalogResourceEndpoint;
    private String geostormRestRasterImportEndpoint;
    private String geostormUsername;
    private String geostormPassword;

    private String geostormCollectionMapfilesPath;
    private String geostormCollectionMapfilesSample;

    private String geostormHostName;
    private String geostormStormConnectionUsername;
    private String geostormSSHConnectionKey;

    private final boolean enabled;

    public GeostormClient() {
        ConfigurationManager configManager = ConfigurationManager.getInstance();
        String enabledString = configManager.getValue("geostorm.integration.enabled", "false");
        enabled = Boolean.parseBoolean(enabledString);
        if (enabled) {
            geostormRestBaseURL = configManager.getValue("geostorm.rest.base.url");
            geostormRestCatalogResourceEndpoint = configManager.getValue("geostorm.rest.catalog.resource.endpoint");
            geostormRestRasterImportEndpoint = configManager.getValue("geostorm.rest.raster.import.endpoint");
            geostormUsername = configManager.getValue("geostorm.admin.username");
            geostormPassword = configManager.getValue("geostorm.admin.password");
            geostormCollectionMapfilesPath = configManager.getValue("geostorm.raster.collection.mapfiles.path");
            geostormCollectionMapfilesSample = configManager.getValue("geostorm.raster.collection.mapfiles.sample");
            geostormHostName = configManager.getValue("geostorm.host.name");
            geostormStormConnectionUsername = configManager.getValue("geostorm.storm.connection.username");
            geostormSSHConnectionKey = configManager.getValue("geostorm.ssh.connection.private.key.file.path");


            if (geostormRestBaseURL == null || geostormRestCatalogResourceEndpoint == null ||
              geostormRestRasterImportEndpoint == null ||
              geostormUsername == null || geostormPassword == null ||
              geostormCollectionMapfilesPath == null || geostormCollectionMapfilesSample == null ||
              geostormHostName == null ||
              geostormStormConnectionUsername == null || geostormSSHConnectionKey == null) {
                throw new UnsupportedOperationException("Geostorm integration plugin not configured");
            }

            restTemplate = new RestTemplate();
            restTemplate.setErrorHandler(new ResponseErrorHandler() {
                @Override
                public boolean hasError(ClientHttpResponse response) throws IOException {
                    boolean hasError = false;
                    int rawStatusCode = response.getRawStatusCode();
                    if (rawStatusCode != 200) {
                        hasError = true;
                    }
                    return hasError;
                }

                @Override
                public void handleError(ClientHttpResponse response) throws IOException {
                    logger.warning(response.getRawStatusCode() + " " + response.getStatusText());
                }
            });
        }
    }

    @Override
    public Class<EOProduct> isIntendedFor() {
        return EOProduct.class;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public List<EOProduct> handle(List<EOProduct> list) throws DataHandlingException {
        if (this.enabled) {
            //importCatalogResources(list);
            importRasters(list);
        }
        return list;
    }

    private void importCatalogResources(List<EOProduct> list) {
        Resource geostormResource;
        for (EOProduct product : list) {
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
                geostormResource.setCoord_sys(new Integer[]{Integer.parseInt(crs)});

                // save resource
                addResource(geostormResource);
            } catch (Exception ex) {
                logger.severe(ex.getMessage());
            }
        }
    }

    private void importRasters(List<EOProduct> list) {
        RasterProduct geostormRaster;
        for (EOProduct product : list) {
            try {
                // conversion from EOProduct to RasterProduct
                if (canCreateCollectionMapFileIfNotExists(product.getProductType())){
                    logger.info("Starting to import " + product.getName());

                    geostormRaster = new RasterProduct();
                    geostormRaster.setProduct_path(product.getLocation());

                    /*// QUICK FIX
                    if(StringUtils.isNullOrEmpty(product.getUserName())){
                        geostormRaster.setOwner("admin");
                    }
                    else {
                        geostormRaster.setOwner(product.getUserName());
                    }*/
                    geostormRaster.setOwner("admin");


                    geostormRaster.setCollection(product.getProductType());
                    geostormRaster.setSite("Site"); // TODO see where and how it's used
                    geostormRaster.setMosaic_name("Mosaic_" + product.getProductType()); // TODO see if name matters

                    String entryPoint = product.getEntryPoint();
                    if (entryPoint == null) {
                        entryPoint = product.getLocation();
                    }
                    geostormRaster.setEntry_point(new String[]{entryPoint});
                    geostormRaster.setProduct_date(dateFormatter.format(product.getAcquisitionDate()));
                    geostormRaster.setExtent(convertWKTToGeoJson(product.getGeometry()));
                    String crs = product.getCrs();
                    if (crs.contains(":")) {
                        crs = crs.substring(crs.indexOf(":") + 1);
                    }
                    geostormRaster.setOrganization(getUserOrganization(product.getUserName()));

                    // import raster
                    logger.info("raster import, product_path=" + geostormRaster.getProduct_path() + ", entry_point(s)=" + Arrays.toString(geostormRaster.getEntry_point()));
                    importRaster(geostormRaster);
                }
                else {
                    logger.warning("Cannot manage collection map file for " + product.getName() + " of type " + product.getProductType());
                }

            } catch (Exception ex) {
                logger.severe(ex.getMessage());
            }
        }
    }

    public String getResources() {
        trustSelfSignedSSL();
        ResponseEntity<String> result;
        final HttpHeaders headers = createHeaders(geostormUsername, geostormPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);
        final HttpEntity<String> httpEntity = new HttpEntity<String>(headers);
        final String url = geostormRestBaseURL + geostormRestCatalogResourceEndpoint;
        logger.fine("URL = " + url);
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
        logger.fine("URL = " + url);
        logger.fine("Headers = " + headers.toString());
        try {
            logger.fine("Body = " + new ObjectMapper().writeValueAsString(httpEntity));
        } catch (JsonProcessingException e) {
            logger.severe(String.format("addResource(): Body JSON exception '%s'", e.getMessage()));
        }
        result = restTemplate.postForEntity(url, httpEntity, String.class);
        try {
            logger.fine("addResource result:" + new ObjectMapper().writeValueAsString(result));
        } catch (JsonProcessingException e) {
            logger.severe(String.format("addResource(): Result JSON exception '%s'", e.getMessage()));
        }
        return result.getBody();
    }

    public String importRaster(RasterProduct rasterProduct) {
        trustSelfSignedSSL();
        ResponseEntity<String> result;
        final HttpHeaders headers = createHeaders(geostormUsername, geostormPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);
        final HttpEntity<RasterProduct> httpEntity = new HttpEntity<RasterProduct>(rasterProduct, headers);
        final String url = geostormRestBaseURL + geostormRestRasterImportEndpoint;
        logger.info("URL = " + url);
        logger.info("Headers = " + headers.toString());
        try {
            logger.info("Body = " + new ObjectMapper().writeValueAsString(httpEntity));
        } catch (JsonProcessingException e) {
            logger.severe(String.format("importRaster(): Body JSON exception '%s'", e.getMessage()));
        }
        result = restTemplate.postForEntity(url, httpEntity, String.class);
        try {
            logger.info("importRaster result:" + new ObjectMapper().writeValueAsString(result));
        } catch (JsonProcessingException e) {
            logger.severe(String.format("importRaster(): Result JSON exception '%s'", e.getMessage()));
        }
        return result.getBody();
    }

    private void prepareSSL() {
        System.setProperty("javax.net.ssl.trustStore", "*");
        System.setProperty("javax.net.ssl.trustStorePassword", "");
    }

    static void trustSelfSignedSSL() {
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
            ctx.init(null, new TrustManager[]{tm}, null);
            SSLContext.setDefault(ctx);
        } catch (Exception ex) {
            logger.severe(ex.getMessage());
        }
    }

    private HttpHeaders createHeaders(final String username, final String password) {
        return new HttpHeaders() {{
            String auth = username + ":" + password;
            byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("US-ASCII")));
            String authHeader = "Basic " + new String(encodedAuth);
            set("Authorization", authHeader);
        }};
    }

    private String convertWKTToGeoJson(final String wkt) {
        GeometryJSON geoJson = new GeometryJSON(8);
        GeometryAdapter adapter = new GeometryAdapter();
        try {
            return geoJson.toString(adapter.marshal(wkt));
        } catch (Exception e) {
            logger.warning(e.getMessage());
            return null;
        }
    }

    private String getUserOrganization(final String username) {
        /*String organization = "";
        try {
            organization = persistenceMng.getUserOrganization(username);
        } catch (PersistenceException e) {
            e.printStackTrace();
        }
        return organization;*/
        return "CSRO";
    }

    boolean canCreateCollectionMapFileIfNotExists(final String collectionName){
        // check first if the file already exists
        String collectionMapFile = geostormCollectionMapfilesPath + collectionName + ".map";

        Session session = null;
        Channel channel = null;

        try {
            String command = "cp " + geostormCollectionMapfilesSample + " " + collectionMapFile;

            JSch jsch = new JSch();
            jsch.addIdentity(geostormSSHConnectionKey);
            //System.out.println("identity added ");

            session = jsch.getSession(geostormStormConnectionUsername, geostormHostName, 22);
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            //System.out.println("session created.");

            session.connect();
            //System.out.println("session connected.....");

            channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            ((ChannelExec) channel).setPty(false);
            channel.connect();
            channel.disconnect();
            session.disconnect();

            return true;

        } catch (JSchException e) {
            logger.log(Level.SEVERE, "Error during SSH command execution", e);
            if (session != null && session.isConnected()){
                session.disconnect();
            }
            if (channel != null && channel.isConnected()){
                channel.disconnect();
            }
        }

        return false;
    }
}
