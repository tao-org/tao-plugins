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
package ro.cs.tao.datasource.remote.peps.download;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.peps.PepsDataSource;
import ro.cs.tao.datasource.remote.peps.PepsMetadataResponseHandler;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.products.sentinels.SentinelProductHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/**
 * @author Cosmin Cara
 */
public class PepsDownloadStrategy extends DownloadStrategy<String> {
    private static final Properties properties;
    private int retries;

    static {
        properties = new Properties();
        try {
            properties.load(PepsDataSource.class.getResourceAsStream("peps.properties"));
        } catch (IOException ignored) {
        }
    }

    public PepsDownloadStrategy(PepsDataSource dataSource, String targetFolder) {
        super(dataSource, targetFolder, properties);
        retries = Integer.parseInt(properties.getProperty("peps.wait.retries", "5"));
    }

    private PepsDownloadStrategy(PepsDownloadStrategy other) {
        super(other);
        this.retries = other.retries;
    }

    @Override
    public PepsDownloadStrategy clone() { return new PepsDownloadStrategy(this); }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException {
        FileUtilities.ensureExists(Paths.get(destination));
        String productName = product.getName();
        currentStep = "Metadata";
        ProductState productState;
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, getMetadataUrl(product), this.credentials)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    JsonResponseParser<Boolean> parser = new JsonResponseParser<>(new PepsMetadataResponseHandler());
                    List<Boolean> parse = parser.parse(EntityUtils.toString(response.getEntity()));
                    productState = parse.get(0) ? ProductState.AVAILABLE : ProductState.ON_TAPE;
                    break;
                case 401:
                    throw new QueryException("The supplied credentials are invalid or the product was not found!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                                                           response.getStatusLine().getReasonPhrase()));
            }
        } catch (IOException ex) {
            throw new QueryException(ex);
        }
        currentStep = "Archive";
        Path rootPath = Paths.get(destination, productName + ".zip");
        Path productFile = null;
        switch (productState) {
            case ON_TAPE:
                while (retries >= 0) {
                    try {
                        int maxRetries = Integer.parseInt(properties.getProperty("peps.wait.retries", "5"));
                        logger.info(String.format("Product [%s] is stored on tape, retrying after 30 seconds (retry %s of %s)",
                                                  productName, maxRetries - retries + 1, maxRetries + 1));
                        Thread.sleep(30000);
                    } catch (InterruptedException ignored) { }
                    retries--;
                    productFile = fetch(product);
                }
                break;
            case AVAILABLE:
                productFile = downloadFile(getProductUrl(product), rootPath, this.dataSource.authenticate());
                break;
        }
        if (productFile != null) {
            FileUtilities.unzip(productFile);
            ProductHelper helper = SentinelProductHelper.create(productName);
            product.setEntryPoint(helper.getMetadataFileName());
        }
        return productFile;
    }

    @Override
    protected String getMetadataUrl(EOProduct descriptor) {
        String location = descriptor.getLocation();
        if (location.endsWith("/")) {
            location = location.substring(0, location.length() - 1);
        }
        return location.replace("/download", "");
    }

    @Override
    public String getProductUrl(EOProduct descriptor) {
        String location = super.getProductUrl(descriptor);
        return location != null ? location + "/?issuerId=peps" : null;
    }

    private enum ProductState {
        AVAILABLE,
        ON_TAPE,
        ERROR
    }
}
