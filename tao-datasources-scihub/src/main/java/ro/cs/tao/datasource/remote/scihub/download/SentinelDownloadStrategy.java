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
package ro.cs.tao.datasource.remote.scihub.download;

import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.products.sentinels.SentinelProductHelper;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class SentinelDownloadStrategy extends DownloadStrategy<String> {

    static final String ODATA_XML_PLACEHOLDER = "${xmlname}";
    static final String ODATA_UUID = "${UUID}";
    static final String ODATA_PRODUCT_NAME = "${PRODUCT_NAME}";

    private static final Properties properties;

    String oDataBasePath;
    String odataArchivePath;

    static {
        properties = new Properties();
        try {
            properties.load(SciHubDataSource.class.getResourceAsStream("scihub.properties"));
        } catch (IOException ignored) {
        }
    }

    public SentinelDownloadStrategy(SciHubDataSource dataSource, String targetFolder) {
        super(dataSource, targetFolder, properties);
        ODataPath odp = new ODataPath();
        String scihubUrl = props.getProperty("scihub.product.url", "https://scihub.copernicus.eu/apihub/odata/v1");
        /*if (!NetUtils.isAvailable(scihubUrl)) {
            System.err.println(scihubUrl + " is not available!");
            scihubUrl = props.getProperty("scihub.product.backup.url", "https://scihub.copernicus.eu/dhus/odata/v1");
        }*/
        oDataBasePath = odp.root(scihubUrl + "/Products('${UUID}')").path();
        odataArchivePath = odp.root(scihubUrl + "/Products('${UUID}')").value();
    }

    protected SentinelDownloadStrategy(SentinelDownloadStrategy other) {
        super(other);
        this.odataArchivePath = other.odataArchivePath;
        this.oDataBasePath = other.oDataBasePath;
    }

    @Override
    public SentinelDownloadStrategy clone() { return new SentinelDownloadStrategy(this); }

    @Override
    public String getProductUrl(EOProduct descriptor) {
        return descriptor.getLocation() == null ?
                odataArchivePath.replace(ODATA_UUID, descriptor.getId()) :
                descriptor.getLocation();
    }

    public String getProductOnlineStatusUrl(EOProduct descriptor) {
        return descriptor.getLocation() == null ?
                odataArchivePath.replace(ODATA_UUID, descriptor.getId()).replace("$value", "Online/$value") :
                descriptor.getLocation().endsWith("$value") ?
                        descriptor.getLocation().replace("$value", "Online/$value") :
                        null;
    }

    @Override
    protected boolean adjustProductLength() { return true; }

    @Override
    protected String getMetadataUrl(EOProduct descriptor) {
        return null;
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
        FileUtilities.ensureExists(Paths.get(destination));
        String productName = product.getName();
        currentStep = "Archive";
        Path rootPath = Paths.get(destination, productName + ".zip");
        if (this.currentProduct == null) {
            this.currentProduct = product;
        }
        /* The status=archived received from ApiHUB doesn't indicate that the product is offline!
        if ("archived".equalsIgnoreCase(product.getAttributeValue("status"))) {
            throw new IOException(String.format("Product %s is marked as archived", productName));
        }*/
        final String statusUrl = getProductOnlineStatusUrl(product);
        if (statusUrl != null) {
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, statusUrl, this.credentials)) {
                int statusCode = response.getStatusLine().getStatusCode();
                logger.finest(String.format("GET %s received status code %d and content", statusUrl, statusCode));
                switch (statusCode) {
                    case 200:
                        final String body = EntityUtils.toString(response.getEntity());
                        logger.finest(String.format("Content: %s", body));
                        if (!Boolean.parseBoolean(body)) {
                            throw new IOException(String.format("Product %s is not online", productName));
                        }
                        break;
                    case 401:
                        throw new IOException("The supplied credentials are invalid or the user is unauthorized");
                    default:
                        throw new IOException(String.format("The request was not successful. Reason: %s",
                                                            response.getStatusLine().getReasonPhrase()));
                }
            }
        }
        final String productURL = getProductUrl(product);
        final String token = this.dataSource.authenticate();
        if (product.getApproximateSize() < 1) {
            updateProductApproximateSize(product, productURL, token);
        }
        return downloadFile(productURL, rootPath, token);
    }

    protected void updateProductApproximateSize(EOProduct product, String productURL, String token) {
        final HttpURLConnection connection = NetUtils.openConnection(productURL, token);
        final long remoteFileLength = connection.getContentLengthLong();
        if (remoteFileLength > 0) {
            product.setApproximateSize(remoteFileLength);
        }
        connection.disconnect();
    }

    @Override
    protected Path link(EOProduct product, Path sourceRoot, Path targetRoot) throws IOException {
        Path path = super.link(product, sourceRoot, targetRoot);
        if (path != null && product.getAttributeValue("tiles") == null) {
            ProductHelper helper = SentinelProductHelper.create(product.getName());
            if (helper instanceof Sentinel2ProductHelper) {
                Sentinel2ProductHelper s2Helper = (Sentinel2ProductHelper) helper;
                product.addAttribute("tiles", s2Helper.getTileIdentifier());
            }
        }
        return path;
    }

    @Override
    protected Path check(EOProduct product, Path sourceRoot) throws IOException {
        Path path = super.check(product, sourceRoot);
        if (path != null) {
            ProductHelper helper = SentinelProductHelper.create(product.getName());
            if (helper instanceof Sentinel2ProductHelper) {
                Sentinel2ProductHelper s2Helper = (Sentinel2ProductHelper) helper;
                product.addAttribute("tiles", s2Helper.getTileIdentifier());
            }
        }
        return path;
    }

    static class ODataPath {
        private StringBuilder buffer;

        ODataPath() {
            buffer = new StringBuilder();
        }

        ODataPath root(String path) {
            buffer.setLength(0);
            buffer.append(path);
            return this;
        }

        ODataPath node(String nodeName) {
            buffer.append("/Nodes('").append(nodeName).append("')");
            return this;
        }

        String path() {
            return buffer.toString();
        }

        String value() {
            return buffer.toString() + "/$value";
        }
    }
}
