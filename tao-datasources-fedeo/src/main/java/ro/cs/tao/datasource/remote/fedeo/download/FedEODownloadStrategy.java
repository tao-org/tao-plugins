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
package ro.cs.tao.datasource.remote.fedeo.download;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.fedeo.FedEODataSource;
import ro.cs.tao.datasource.remote.fedeo.auth.FedEOAuthentication;
import ro.cs.tao.datasource.util.Zipper;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * FedEO Download strategy
 *
 * @author Valentin Netoiu
 */
public class FedEODownloadStrategy extends DownloadStrategy<Header> {
    private static final String ZIP_EXTENSION = ".zip";
    private static final long DOWNLOAD_TIMEOUT = 30000; // 30s
    private Timer timeoutTimer;

    public FedEODownloadStrategy(FedEODataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
    }

    protected FedEODownloadStrategy(FedEODownloadStrategy other) {
        super(other);
    }


    @Override
    public FedEODownloadStrategy clone() {
        return new FedEODownloadStrategy(this);
    }

    @Override
    protected String getMetadataUrl(EOProduct descriptor) {
        throw new RuntimeException("Metadata file not supported for this strategy");
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException {
        checkCancelled();
        return connectAndDownload(product);
    }

    /**
     * Connect to the product download url and download the product.
     *
     * @param product - The requested EOProduct to download
     * @return the Path to downloaded product
     * @throws IOException in case of any error occurs
     */
    protected Path connectAndDownload(EOProduct product) throws QueryException, IOException {
        HttpURLConnection connection = getConnectionForProduct(product);
        return downloadProduct(product, connection);
    }

    protected HttpURLConnection getConnectionForProduct(EOProduct product) {
        int responseStatus;

        // First set the default cookie manager.
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        String productDownloadUrl = getProductUrl(product);
        try {
            Properties authProperties = new Properties();
            authProperties.setProperty(FedEOAuthentication.DOWNLOAD_URL_PROPERTY_NAME, productDownloadUrl);
            this.dataSource.setAdditionalProperties(authProperties);
            final Header authHeader = this.dataSource.authenticate();
            if (authHeader == null) {
                throw new IOException("Invalid credentials");
            }
            List<NameValuePair> requestProperties = new ArrayList<>();
            HttpURLConnection connection = NetUtils.openURLConnection(HttpMethod.GET, productDownloadUrl, authHeader, requestProperties);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            responseStatus = connection.getResponseCode();
            switch (responseStatus){
                case HttpStatus.SC_OK:
                    return connection;
                case HttpStatus.SC_ACCEPTED:
                    throw new QueryException(String.format("The request was successful. response code: %d: The download will be ready soon. Try again later.",
                            connection.getResponseCode()));
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: response code: %d: response message: %s",
                            connection.getResponseCode(), connection.getResponseMessage()));
            }
        } catch (IOException e) {
            throw new QueryException(String.format("The request was not successful. Reason: %s", e.getMessage()));
        }
    }

    protected Path downloadProduct(EOProduct product, HttpURLConnection connection) throws IOException {
        Path productFile;
        try {
            String productUrl = getProductUrl(product);
            String extension = getExtensionFromUrl(productUrl);
            boolean isArchive = !extension.isEmpty() &&
                    (extension.equalsIgnoreCase(ZIP_EXTENSION));
            subActivityStart(product.getName());
            final String archiveName = (product.getId() != null ? product.getId() : product.getName()) + extension;
            Path archivePath = Paths.get(destination, archiveName);
            FileUtilities.ensureExists(Paths.get(destination));
            Files.deleteIfExists(archivePath);
            SeekableByteChannel outputStream = null;
            long size = currentProduct.getApproximateSize();
            long length = connection.getContentLength();
            if (length == -1) {
                //try to get the length from header
                length = connection.getHeaderFieldLong("Content-Length", size);
            }

            if (size > length) {
                Path existingProduct = Paths.get(destination, product.getName() + ".SAFE");
                if (Files.exists(existingProduct)) {
                    long existingSize = FileUtilities.folderSize(existingProduct);
                    logger.fine(String.format("Product %s found: %s; size: %d, expected: %s",
                            product.getName(), existingProduct, existingSize, size));
                    if (existingSize >= size) {
                        logger.fine("Download will be skipped");
                        try {
                            product.setLocation(existingProduct.toUri().toString());
                        } catch (URISyntaxException e) {
                            logger.severe(e.getMessage());
                        }
                        return existingProduct;
                    } else {
                        logger.fine("Download will be attempted again");
                    }
                }
            }
            currentProduct.setApproximateSize(length);
            currentProductProgress = new ProductProgress(currentProduct.getApproximateSize(), isArchive);
            try (InputStream inputStream = connection.getInputStream()) {
                outputStream = Files.newByteChannel(archivePath, EnumSet.of(StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE));
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                logger.finest("Begin reading from input stream");
                if (this.timeoutTimer == null) {
                    this.timeoutTimer = new Timer("Timeout");
                }
                TimerTask task;
                final long connTimeout = (connection.getReadTimeout() <= 0 ? DOWNLOAD_TIMEOUT : connection.getReadTimeout());
                while (!isCancelled() && (read = inputStream.read(buffer)) != -1) {
                    task = new TimerTask() {
                        @Override
                        public void run() {
                            logger.warning(String.format("Remote host did not send anything for %d seconds, cancelling download",
                                    connTimeout / 1000));
                            FedEODownloadStrategy.this.cancel();
                        }
                    };
                    this.timeoutTimer.schedule(task, connTimeout);
                    outputStream.write(ByteBuffer.wrap(buffer, 0, read));
                    currentProductProgress.add(read);
                    task.cancel();
                    this.timeoutTimer.purge();
                }
                outputStream.close();
                logger.finest("End reading from input stream");
                checkCancelled();
                if (isArchive && Boolean.parseBoolean(this.props.getProperty("auto.uncompress", "true"))) {
                    productFile = extract(archivePath, computeTarget(archivePath));
                } else {
                    productFile = archivePath;
                }
                if (productFile != null) {
                    try {
                        product.setLocation(productFile.toUri().toString());
                    } catch (URISyntaxException e) {
                        logger.severe(e.getMessage());
                    }
                }
            } finally {
                if (outputStream != null && outputStream.isOpen()) outputStream.close();
                if (this.timeoutTimer != null) {
                    this.timeoutTimer.cancel();
                    this.timeoutTimer = null;
                }
            }
            logger.fine(String.format("End download for %s", product.getName()));
        } finally {
            subActivityEnd(product.getName());
        }

        return productFile;
    }

    protected Path computeTarget(Path archivePath) {
        return archivePath.getFileName().toString().endsWith(".tar.gz") ?
                Paths.get(archivePath.toString().replace(".tar.gz", "")) :
                Paths.get(archivePath.toString().replace(".zip", ""));
    }

    protected Path extract(Path archivePath, Path targetPath) throws IOException {
        logger.fine(String.format("Begin decompressing %s into %s", archivePath.getFileName(), targetPath));
        Path result = archivePath.toString().endsWith(".tar.gz") ?
                Zipper.decompressTarGz(archivePath, targetPath, true) :
                Zipper.decompressZip(archivePath, targetPath, true);
        logger.fine(String.format("Decompression of %s completed", archivePath.getFileName()));
        return result;
    }

    protected String getExtensionFromUrl(String url) {
        String normalizedUrl = url;
        if (url.indexOf('?') > 0) {
            normalizedUrl = url.substring(0, url.indexOf('?'));
        }
        if (normalizedUrl.toLowerCase().endsWith(ZIP_EXTENSION)) {
            return ZIP_EXTENSION;
        } else {
            //try to get the extension
            String fileName = normalizedUrl.substring(normalizedUrl.lastIndexOf("/"));
            if (fileName.contains(".")) {
                return fileName.substring(fileName.lastIndexOf("."));
            }
        }

        return "";
    }

}
