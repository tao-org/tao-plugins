package ro.cs.tao.datasource.remote.mundi;

import org.apache.http.Header;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.SimpleArchiveDownloadStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.products.sentinels.Sentinel1ProductHelper;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
import java.util.regex.Pattern;

public class DownloadStrategy extends SimpleArchiveDownloadStrategy {

    private static final Pattern mundiPathPattern = Pattern.compile("s1-l1-slc-(\\d{4})-q(\\d{1})\\/(\\d{4}\\/\\d{2}\\/\\d{2}\\/\\w{2}\\/\\w{2})\\/[\\S]+");

    public DownloadStrategy(MundiDataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
        this.fetchMode = FetchMode.OVERWRITE;
    }

    private DownloadStrategy(DownloadStrategy other) {
        super(other);
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
        checkCancelled();
        final Header authHeader = ((MundiDataSource) this.dataSource).authenticate();
        if (authHeader == null) {
            throw new IOException("Invalid credentials");
        }
        Path productFile;
        if (currentProduct == null) {
            currentProduct = product;
        }
        String productUrl = getProductUrl(product);
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
                                                DownloadStrategy.this.cancel();
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

    @Override
    public String getProductUrl(EOProduct descriptor) {
        URI productUrl = null;
        final String location = descriptor.getLocation();
        String baseUrl = this.props.getProperty("sentinel.download.url", "https://obs.eu-de.otc.t-systems.com/");
        try {
            try {
                productUrl = URI.create(location);
                if (productUrl.getScheme() == null || !productUrl.getScheme().startsWith("http")) {
                    throw new IllegalArgumentException();
                }
                // if we get here, the location is a URL
                if (location.startsWith(baseUrl)) {
                    // the location is a MUNDI URL
                    productUrl = new URI(location);
                } else {
                    // the location is not a MUNDI URL, we must compute it
                    productUrl = new URI(baseUrl + computeRelativeLocation(descriptor));
                }
            } catch (IllegalArgumentException ignored) {
                // the location is not a URL, it should be relative already
                if (mundiPathPattern.matcher(location).find()) {
                    // the location is a MUNDI relative path
                    productUrl = new URI(baseUrl + location);
                } else {
                    productUrl = new URI(baseUrl + computeRelativeLocation(descriptor));
                }
            }
        } catch (URISyntaxException ignored) {
        }
        return productUrl != null ? productUrl.toString() : null;
    }

    @Override
    public SimpleArchiveDownloadStrategy clone() {
        return new DownloadStrategy(this);
    }

    @Override
    protected Path computeTarget(Path archivePath) {
        return archivePath.getParent();
    }

    private String computeRelativeLocation(EOProduct descriptor) {
        Sentinel1ProductHelper helper = new Sentinel1ProductHelper(descriptor.getName());
        StringBuilder builder = new StringBuilder();
        String sensingDate = helper.getSensingDate();
        final int month = Integer.parseInt(sensingDate.substring(4, 6));
        builder.append("s1-l1-slc-")
                .append(sensingDate, 0, 4).append("-")
                .append("q").append(month < 4 ? 1 : month < 7 ? 2 : month < 10 ? 3 : 4).append("/")
                .append(sensingDate, 0, 4).append("/")
                .append(sensingDate, 4, 6).append("/")
                .append(sensingDate, 6, 8).append("/")
                .append(helper.getSensorMode().name()).append("/")
                .append(helper.getPolarisation().name()).append("/").append(descriptor.getName()).append(".zip");
        return builder.toString();
    }
}
