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
package ro.cs.tao.datasource.remote.asf.download;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import ro.cs.tao.datasource.ProductHelperFactory;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.asf.ASFDataSource;
import ro.cs.tao.datasource.util.Zipper;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.utils.FileUtilities;
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
import java.util.EnumSet;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ASF Download strategy
 *
 * @author Valentin Netoiu
 */
public class AsfDownloadStrategy extends DownloadStrategy<String> {
    private static final String ZIP_EXTENSION = ".zip";
    private static final String TAR_GZ_EXTENSION = ".tar.gz";
    private static final String KMZ_EXTENSION = ".kmz";
    private static final long DOWNLOAD_TIMEOUT = 30000; // 30s
    private Timer timeoutTimer;

    public AsfDownloadStrategy(ASFDataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
    }

    protected AsfDownloadStrategy(AsfDownloadStrategy other) {
        super(other);
    }


    @Override
    public AsfDownloadStrategy clone() {
        return new AsfDownloadStrategy(this);
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
        int numberOfTries = 0;
        int responseStatus;

        // First set the default cookie manager.
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        String productDownloadUrl = getProductUrl(product);

        try {
            final String token = this.dataSource.authenticate();
            HttpURLConnection connection = NetUtils.openConnection(productDownloadUrl, token);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            do {
                responseStatus = connection.getResponseCode();
                switch (responseStatus) {
                    case HttpStatus.SC_OK:
                        final String requestUrl = connection.getURL().toString();
                        if (requestUrl.toLowerCase().contains("token")) {
                            connection = NetUtils.openConnection(requestUrl, (Header) null);
                        } else {
                            connection = NetUtils.openConnection(requestUrl, token);
                        }
                        connection.setInstanceFollowRedirects(true);
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36 Edg/112.0.1722.58");
                        connection.connect();
                        return connection;
                    case HttpStatus.SC_UNAUTHORIZED:
                        connection = NetUtils.openConnection(connection.getURL().toString(), token);
                        connection.setInstanceFollowRedirects(true);
                        connection.connect();
                        numberOfTries++;
                        break;
                    default:
                        throw new QueryException(String.format("The request was not successful. Reason: response code: %d: response message: %s",
                                connection.getResponseCode(), connection.getResponseMessage()));

                }
            } while (numberOfTries < 3);
        } catch (IOException e) {
            throw new QueryException(String.format("The request was not successful. Reason: %s", e.getMessage()));
        }

        throw new QueryException("The request was not successful. Reason: Too many redirects!");
    }

    protected Path downloadProduct(EOProduct product, HttpURLConnection connection) throws IOException {
        Path productFile;
        try {
            String productUrl = getProductUrl(product);
            String extension = getExtensionFromUrl(productUrl);
            boolean isArchive = !extension.isEmpty() &&
                    (extension.equalsIgnoreCase(ZIP_EXTENSION) ||
                            extension.equalsIgnoreCase(TAR_GZ_EXTENSION) ||
                            extension.equalsIgnoreCase(KMZ_EXTENSION));
            subActivityStart(product.getName());
            final String archiveName = (product.getId() != null ? product.getId() : product.getName()) + extension;
            Path archivePath = Paths.get(destination, archiveName);
            FileUtilities.ensureExists(Paths.get(destination));
            Files.deleteIfExists(archivePath);
            SeekableByteChannel outputStream = null;
            long size = currentProduct.getApproximateSize();
            //long length = connection.getContentLength();
            long length = -1;//connection.getHeaderFieldLong("Content-Length", size);
            /*if (length == -1) {
                //try to get the length from header
                length = connection.getHeaderFieldLong("Content-Length", size);
            }*/

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
                            AsfDownloadStrategy.this.cancel();
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
                        ProductHelper helper = ProductHelperFactory.getHelper(product.getName());
                        if (helper != null) {
                            product.setEntryPoint(helper.getMetadataFileName());
                        }
                    } catch (Exception e) {
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
        } else if (normalizedUrl.toLowerCase().endsWith(TAR_GZ_EXTENSION)) {
            return TAR_GZ_EXTENSION;
        } else if (normalizedUrl.toLowerCase().endsWith(KMZ_EXTENSION)) {
            return KMZ_EXTENSION;
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
