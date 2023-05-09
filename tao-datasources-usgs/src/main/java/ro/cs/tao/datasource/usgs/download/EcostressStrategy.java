package ro.cs.tao.datasource.usgs.download;

import org.apache.commons.lang.SerializationUtils;
import org.apache.http.HttpStatus;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.SimpleArchiveDownloadStrategy;
import ro.cs.tao.datasource.usgs.USGSDataSource;
import ro.cs.tao.datasource.util.Zipper;
import ro.cs.tao.eodata.EOProduct;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EcostressStrategy extends SimpleArchiveDownloadStrategy {
    private static final String ZIP_EXTENSION = ".zip";
    private static final String TAR_GZ_EXTENSION = ".tar.gz";
    private static final String KMZ_EXTENSION = ".kmz";
    private static final String HDF5_EXTENSION = ".h5";

    public EcostressStrategy(USGSDataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
    }

    protected EcostressStrategy(EcostressStrategy other) {
        super(other);
    }

    @Override
    public EcostressStrategy clone() {
        return new EcostressStrategy(this);
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
        boolean toDownloadBGeo = !(product.getLocation().contains("ECO1BMAPRAD") || product.getLocation().contains("ECO3ETALEXIU") || product.getLocation().contains("ECO4ESIALEXIU") || product.getLocation().contains("ECO1BGEO"));
        HttpURLConnection connection = getConnectionForProduct(product, false);
        EOProduct bgeoProduct = null;
        if (toDownloadBGeo) {
            // product should be cloned before the download occurs, otherwise the location is overwritten
            bgeoProduct = createBGeoProduct(product);
        }
        Path downloaded = downloadProduct(product, connection);
        if (bgeoProduct != null) {
            downloadBGEO(bgeoProduct);
        }
        return downloaded;
    }

    protected HttpURLConnection getConnectionForProduct(EOProduct product, boolean isBGeo) {
        int numberOfTries = 0;
        int responseStatus;

        // First set the default cookie manager.
        CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        String productDownloadUrl = getProductUrl(product);
        if (isBGeo) {
            productDownloadUrl = productDownloadUrl.substring(0, productDownloadUrl.lastIndexOf('.') - 1) + "1" + HDF5_EXTENSION;
        }
        HttpURLConnection connection = null;
        try {
            String token = ((USGSDataSource) this.dataSource).getEarthDataToken();
            connection = NetUtils.openConnection(productDownloadUrl, token);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            do {
                responseStatus = connection.getResponseCode();
                switch (responseStatus) {
                    case HttpStatus.SC_OK:
                        connection = NetUtils.openConnection(connection.getURL().toString(), token);
                        connection.setInstanceFollowRedirects(true);
                        connection.connect();
                        return connection;
                    case HttpStatus.SC_UNAUTHORIZED:
                        connection = NetUtils.openConnection(connection.getURL().toString(), token);
                        connection.setInstanceFollowRedirects(true);
                        connection.connect();
                        numberOfTries++;
                        break;
                    case HttpStatus.SC_NOT_FOUND:
                        if (isBGeo) {
                            productDownloadUrl = productDownloadUrl.substring(0, productDownloadUrl.lastIndexOf('.') - 1) + (numberOfTries + 2) + HDF5_EXTENSION;
                        }
                        numberOfTries++;
                        break;
                    default:
                        throw new QueryException(String.format("The request was not successful. Reason: response code: %d: response message: %s",
                                connection.getResponseCode(), connection.getResponseMessage()));
                }

            } while (numberOfTries < 3);
        } catch (IOException e) {
            throw new QueryException(String.format("The request was not successful. Reason: %s", e.getMessage()));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        throw new QueryException("The request was not successful. Reason: Too many redirects!");
    }

    protected Path downloadProduct(EOProduct product, HttpURLConnection connection) throws IOException {
        Path productFile;
        try {
            String productUrl = getProductUrl(product);
            String extension = getExtensionFromUrl(productUrl).isEmpty() ? HDF5_EXTENSION : getExtensionFromUrl(productUrl);
            boolean isArchive = !extension.isEmpty() &&
                    (extension.equalsIgnoreCase(ZIP_EXTENSION) ||
                            extension.equalsIgnoreCase(TAR_GZ_EXTENSION) ||
                            extension.equalsIgnoreCase(KMZ_EXTENSION));
            final String name = product.getName() != null ? product.getName() : product.getId();
            subActivityStart(name);
            final String archiveName = name.endsWith(extension) ? name : name + extension;
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
                            EcostressStrategy.this.cancel();
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

    protected EOProduct createBGeoProduct(EOProduct product) {
        EOProduct bgeoProduct = (EOProduct) SerializationUtils.clone(product);
        try {
            final String productBGEOUrl = getProductBGEOUrl(bgeoProduct);
            bgeoProduct.setLocation(productBGEOUrl);
            bgeoProduct.setName(getProductBGEOName(bgeoProduct));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return bgeoProduct;
    }

    protected void downloadBGEO(EOProduct bgeoProduct) {
        try {
            final String archiveName = (bgeoProduct.getName() != null ? bgeoProduct.getName() : bgeoProduct.getId()) + HDF5_EXTENSION;
            Path archivePath = Paths.get(destination, archiveName);
            if (!Files.exists(archivePath)) {
                HttpURLConnection connection = getConnectionForProduct(bgeoProduct, true);
                downloadProduct(bgeoProduct, connection);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected String getProductBGEOName(EOProduct product) {
        String sLocation = product.getLocation();
        Pattern findUrl = Pattern.compile(".*/ECOSTRESS_(.+?)(\\.[^.]*$|$)");
        Matcher matcher = findUrl.matcher(sLocation);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return "";
    }

    protected String getProductBGEOUrl(EOProduct product){
        String sLocation = product.getLocation();
        URI location = null;
        try {
            Pattern pattern = Pattern.compile("https?://\\w+.*/(ECO[A-Z])/ECOSTRESS/(\\w+.*)/.*/ECOSTRESS_([A-Za-z0-9]+)_([A-Za-z0-9]+).*.h5$");
            Matcher matcher = pattern.matcher(sLocation);
            if (matcher.matches()) {
                sLocation = sLocation.replace(matcher.group(1), "ECOA");
                sLocation = sLocation.replace(matcher.group(2), "ECO1BGEO.001");
                sLocation = sLocation.replace(matcher.group(3), "L1B");
                sLocation = sLocation.replace(matcher.group(4), "GEO");
            }
            location = new URI(sLocation);
        } catch (URISyntaxException ignored) {
        }
        return location != null ? location.toString() : null;
    }
}
