package ro.cs.tao.datasource.remote.theia.download;

import org.apache.http.Header;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.SimpleArchiveDownloadStrategy;
import ro.cs.tao.datasource.remote.theia.TheiaDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.io.InputStream;
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

public class TheiaDownloadStrategy extends SimpleArchiveDownloadStrategy {

    public TheiaDownloadStrategy(TheiaDataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
    }

    protected TheiaDownloadStrategy(TheiaDownloadStrategy other) {
        super(other);
    }

    @Override
    public SimpleArchiveDownloadStrategy clone() {
        return super.clone();
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
        checkCancelled();
        final Header authHeader = ((TheiaDataSource) this.dataSource).authenticate();
        Path productFile;
        if (currentProduct == null) {
            currentProduct = product;
        }
        String productUrl = getProductUrl(product) + "/?issuerId=theia";
        String extension;
        // It is possible that, for archives, the query string of the URL to contain some authentication/redirection tokens
        if (productUrl.indexOf('?') > 0) {
            String trimmedUrl = productUrl.substring(0, productUrl.indexOf('?'));
            extension = trimmedUrl.endsWith(".zip") ? ".zip" : trimmedUrl.endsWith(".tar.gz") ? ".tar.gz" : "";
        } else {
            extension = productUrl.endsWith(".zip") ? ".zip" : productUrl.endsWith(".tar.gz") ? ".tar.gz" : "";
        }
        boolean isArchive = !extension.isEmpty();
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, productUrl, authHeader, null)) {
            int statusCode = response.getStatusLine().getStatusCode();
            logger.finest(String.format("%s returned http code %s", productUrl, statusCode));
            switch (statusCode) {
                case 200:
                    try {
                        subActivityStart(product.getName());
                        String fileName = null;
                        // try to get file name from header
                        final Header header = response.getFirstHeader("Content-Disposition");
                        if (header != null) {
                            final String value = header.getValue();
                            int idx = value.indexOf("filename=");
                            if (idx > 0) {
                                fileName = value.substring(idx + 10, value.length() - 1);
                                extension = fileName.toLowerCase().endsWith(".zip") ? ".zip" : fileName.toLowerCase().endsWith(".tar.gz") ? ".tar.gz" : "";
                                isArchive = !extension.isEmpty();
                            }
                        }
                        if (fileName == null) {
                            fileName = product.getName() + extension;
                        }
                        final Path archivePath = Paths.get(destination, fileName);
                        FileUtilities.ensureExists(Paths.get(destination));
                        Files.deleteIfExists(archivePath);
                        SeekableByteChannel outputStream = null;
                        long length = response.getEntity().getContentLength();
                        System.out.println("Received content length:" + length);
                        long size = currentProduct.getApproximateSize();
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
                        try (InputStream inputStream = response.getEntity().getContent()) {
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
                            while (!isCancelled() && (read = inputStream.read(buffer)) != -1) {
                                task = new TimerTask() {
                                    @Override
                                    public void run() {
                                        logger.warning(String.format("Remote host did not send anything for %d seconds, cancelling download",
                                                DOWNLOAD_TIMEOUT / 1000));
                                        TheiaDownloadStrategy.this.cancel();
                                    }
                                };
                                this.timeoutTimer.schedule(task, DOWNLOAD_TIMEOUT);
                                outputStream.write(ByteBuffer.wrap(buffer, 0, read));
                                currentProductProgress.add(read);
                                task.cancel();
                                this.timeoutTimer.purge();
                            }
                            outputStream.close();
                            logger.finest("End reading from input stream");
                            checkCancelled();
                            if (Boolean.parseBoolean(this.props.getProperty("auto.uncompress", "true"))) {
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
                    break;
                case 401:
                    throw new QueryException("The supplied credentials are invalid!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                            response.getStatusLine().getReasonPhrase()));
            }
        }
        return productFile;
    }
}
