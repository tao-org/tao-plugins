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
package ro.cs.tao.datasource.remote.lsa.download;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.lsa.LSADataSource;
import ro.cs.tao.datasource.remote.lsa.auth.LSAAuthentication;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.utils.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * EO-CAT Download strategy
 *
 * @author Valentin Netoiu
 */
public class LSADownloadStrategy extends DownloadStrategy<Header> {
    private static final String ZIP_EXTENSION = ".zip";
    private static final long DOWNLOAD_TIMEOUT = 30000; // 30s
    private Timer timeoutTimer;

    public LSADownloadStrategy(LSADataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
    }

    protected LSADownloadStrategy(LSADownloadStrategy other) {
        super(other);
    }


    @Override
    public LSADownloadStrategy clone() {
        return new LSADownloadStrategy(this);
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
        final CloseableHttpResponse connection = getConnectionForProduct(product);
        if (connection.getFirstHeader("Content-Type").getValue().equals("application/zip")) {
            return downloadProduct(product, connection);
        } else {
            return null;
        }
    }

    protected CloseableHttpResponse getConnectionForProduct(EOProduct product) {
        int responseStatus;

        // First set the default cookie manager.
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        final String productDownloadUrl = getProductUrl(product);
        try {
            final Properties authProperties = new Properties();
            authProperties.setProperty(LSAAuthentication.DOWNLOAD_URL_PROPERTY_NAME, productDownloadUrl);
            this.dataSource.setAdditionalProperties(authProperties);
            final Header authHeader = this.dataSource.authenticate();
            if (authHeader == null) {
                throw new IOException("Invalid credentials");
            }
            final List<NameValuePair> requestProperties = new ArrayList<>();
            final CloseableHttpResponse connection = NetUtils.openConnection(HttpMethod.GET, productDownloadUrl, authHeader, requestProperties);
            responseStatus = connection.getStatusLine().getStatusCode();
            switch (responseStatus) {
                case HttpStatus.SC_OK:
                    return connection;
                case HttpStatus.SC_ACCEPTED:
                    throw new QueryException(String.format("The request was successful. response code: %d: The download will be ready soon. Try again later.",
                            connection.getStatusLine().getStatusCode()));
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: response code: %d: response message: %s",
                            connection.getStatusLine().getStatusCode(), connection.getStatusLine().getReasonPhrase()));
            }
        } catch (IOException e) {
            throw new QueryException(String.format("The request was not successful. Reason: %s", e.getMessage()));
        }
    }

    protected Path downloadProduct(EOProduct product, CloseableHttpResponse connection) throws IOException {
        Path productFile;
        try {
            final String productUrl = getProductUrl(product);
            final String extension = getExtensionFromUrl(productUrl);
            final boolean isArchive = extension.equalsIgnoreCase(ZIP_EXTENSION);
            subActivityStart(product.getName());
            final String archiveName = (product.getId() != null ? product.getId() : product.getName()) + extension;
            final Path archivePath = Paths.get(destination, archiveName);
            FileUtilities.ensureExists(Paths.get(destination));
            Files.deleteIfExists(archivePath);
            final long size = currentProduct.getApproximateSize();
            long length = Long.parseLong(connection.getFirstHeader("Content-Length").getValue());

            if (size > length) {
                final Path existingProduct = Paths.get(destination, product.getName() + ".SAFE");
                if (Files.exists(existingProduct)) {
                    final long existingSize = FileUtilities.folderSize(existingProduct);
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
            try (final InputStream inputStream = connection.getEntity().getContent()) {
                try (final SeekableByteChannel outputStream = Files.newByteChannel(archivePath, EnumSet.of(StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE))) {
                    final byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    logger.finest("Begin reading from input stream");
                    if (this.timeoutTimer == null) {
                        this.timeoutTimer = new Timer("Timeout");
                    }
                    final long connTimeout = DOWNLOAD_TIMEOUT;
                    while (!isCancelled() && (read = inputStream.read(buffer)) != -1) {
                        final TimerTask task = new TimerTask() {
                            @Override
                            public void run() {
                                logger.warning(String.format("Remote host did not send anything for %d seconds, cancelling download",
                                        connTimeout / 1000));
                                LSADownloadStrategy.this.cancel();
                            }
                        };
                        this.timeoutTimer.schedule(task, connTimeout);
                        outputStream.write(ByteBuffer.wrap(buffer, 0, read));
                        currentProductProgress.add(read);
                        task.cancel();
                        this.timeoutTimer.purge();
                    }
                }
                logger.finest("End reading from input stream");
                checkCancelled();
                if (isArchive && Boolean.parseBoolean(this.props.getProperty("auto.uncompress", "false"))) {
                    productFile = extract(archivePath, archivePath.getParent());
                    if (!productFile.toString().contains(product.getName())) {
                        productFile = productFile.resolve(product.getName());
                        if (!productFile.toString().endsWith(".SAFE")) {
                            productFile = Paths.get(productFile + ".SAFE");
                        }
                    }
                } else {
                    productFile = archivePath;
                }
                try {
                    product.setLocation(productFile.toUri().toString());
                    //product.setEntryPoint(SentinelProductHelper.create(product.getName()).getMetadataFileName());
                } catch (URISyntaxException e) {
                    logger.severe(e.getMessage());
                }
            } finally {
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

    protected Path extract(Path archivePath, Path targetPath) throws IOException {
        logger.fine(String.format("Begin decompressing %s into %s", archivePath.getFileName(), targetPath));
        final Path result = archivePath.toString().endsWith(".tar.gz") ?
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
            final String fileName = normalizedUrl.substring(normalizedUrl.lastIndexOf("/"));
            if (fileName.contains(".")) {
                return fileName.substring(fileName.lastIndexOf("."));
            }
        }
        return "";
    }

}
