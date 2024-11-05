package ro.cs.tao.datasource.usgs.download;

import org.apache.http.HttpStatus;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.SimpleArchiveDownloadStrategy;
import ro.cs.tao.datasource.usgs.USGSDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
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

public class USGSDownloadStrategy extends SimpleArchiveDownloadStrategy {
    private static final String ZIP_EXTENSION = ".zip";
    private static final String TAR_GZ_EXTENSION = ".tar.gz";
    private static final String KMZ_EXTENSION = ".kmz";

    public USGSDownloadStrategy(USGSDataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
    }

    protected USGSDownloadStrategy(SimpleArchiveDownloadStrategy other) {
        super(other);
    }

    @Override
    public USGSDownloadStrategy clone() {
        return new USGSDownloadStrategy(super.clone());
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
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
        HttpURLConnection connection = null;
        try {
            connection = NetUtils.openConnection(productDownloadUrl, "");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();
            responseStatus = connection.getResponseCode();
            if (responseStatus == HttpStatus.SC_OK) {
                connection = NetUtils.openConnection(connection.getURL().toString(), "");
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                return connection;
            }
            throw new QueryException(String.format("The request was not successful. Reason: response code: %d: response message: %s", connection.getResponseCode(), connection.getResponseMessage()));
        } catch (InterruptedIOException | ConnectException te){
            throw new QueryException(String.format("The request was timed out. The download will be ready soon. Try again later. Code:  %d", HttpStatus.SC_ACCEPTED));
        } catch (IOException e) {
            throw new QueryException(String.format("The request was not successful. Reason: %s", e.getMessage()));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    protected Path downloadProduct(EOProduct product, HttpURLConnection connection) throws IOException {
        Path productFile;
        try {
            String productUrl = getProductUrl(product);
            String extension = getExtensionFromUrl(productUrl);
            extension = extension.isEmpty() ? ZIP_EXTENSION : extension;
            boolean isArchive = (extension.equalsIgnoreCase(ZIP_EXTENSION) ||
                            extension.equalsIgnoreCase(TAR_GZ_EXTENSION) ||
                            extension.equalsIgnoreCase(KMZ_EXTENSION));
            subActivityStart(product.getName());
            final String archiveName = (product.getName() != null ? product.getName() : product.getId()) + extension;
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
                            USGSDownloadStrategy.this.cancel();
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

