package ro.cs.tao.datasource.remote.creodias.download;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.ProductHelperFactory;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.creodias.CreoDiasDataSource;
import ro.cs.tao.datasource.remote.creodias.model.common.Token;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.utils.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;

public class CreoDIASDownloadStrategy extends DownloadStrategy<Token> {
    private static final int DOWNLOAD_TIMEOUT = 30000; // 30s
    //private Timer timeoutTimer;
    private Token apiKey;

    public CreoDIASDownloadStrategy(CreoDiasDataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
        this.fetchMode = FetchMode.OVERWRITE;
    }

    protected CreoDIASDownloadStrategy(CreoDIASDownloadStrategy other) {
        super(other);
        this.fetchMode = FetchMode.OVERWRITE;
        this.apiKey = other.apiKey;
    }

    @Override
    public CreoDIASDownloadStrategy clone() {
        return new CreoDIASDownloadStrategy(this);
    }

    @Override
    public String getProductUrl(EOProduct descriptor) {
        return this.dataSource.getConnectionString("Download") + descriptor.getId();
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
        checkCancelled();
        Path productFile;
        if (currentProduct == null) {
            currentProduct = product;
        }
        if (this.apiKey == null) {
            this.apiKey = this.dataSource.authenticate();
        }
        String productUrl = getProductUrl(product);
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("token", this.apiKey.getAccessToken()));
        productUrl += "?" + URLEncodedUtils.format(params, "UTF-8");
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, productUrl, (List<Header>)null, null, DOWNLOAD_TIMEOUT)) {
            int statusCode = response.getStatusLine().getStatusCode();
            logger.finest(String.format("%s returned http code %s", productUrl, statusCode));
            switch (statusCode) {
                case 200:
                    try {
                        subActivityStart(product.getName());
                        String fileName = null;
                        // try to get file name from header
                        final Header header = response.getFirstHeader("Content-Disposition");
                        String extension = ".zip";
                        if (header != null) {
                            final String value = header.getValue();
                            int idx = value.indexOf("filename=");
                            if (idx > 0) {
                                fileName = value.substring(idx + 9);
                                extension = fileName.toLowerCase().endsWith(".zip") ? ".zip" : fileName.toLowerCase().endsWith(".tar.gz") ? ".tar.gz" : "";
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
                        currentProductProgress = new ProductProgress(currentProduct.getApproximateSize(), true);
                        try (InputStream inputStream = response.getEntity().getContent()) {
                            outputStream = Files.newByteChannel(archivePath, EnumSet.of(StandardOpenOption.CREATE,
                                    StandardOpenOption.APPEND,
                                    StandardOpenOption.WRITE));
                            byte[] buffer = new byte[BUFFER_SIZE];
                            int read;
                            logger.finest("Begin reading from input stream");
                            /*if (this.timeoutTimer == null) {
                                this.timeoutTimer = new Timer("Timeout");
                            }
                            TimerTask task;*/
                            while (!isCancelled() && (read = inputStream.read(buffer)) != -1) {
                                /*task = new TimerTask() {
                                    @Override
                                    public void run() {
                                        logger.warning(String.format("Remote host did not send anything for %d seconds, cancelling download",
                                                DOWNLOAD_TIMEOUT / 1000));
                                        CreoDIASDownloadStrategy.this.cancel();
                                    }
                                };
                                this.timeoutTimer.schedule(task, DOWNLOAD_TIMEOUT);*/
                                outputStream.write(ByteBuffer.wrap(buffer, 0, read));
                                currentProductProgress.add(read);
                                /*task.cancel();
                                this.timeoutTimer.purge();*/
                            }
                            outputStream.close();
                            logger.finest("End reading from input stream");
                            checkCancelled();
                            if (Boolean.parseBoolean(this.props.getProperty("auto.uncompress", "true"))) {
                                productFile = extract(product, archivePath, computeTarget(archivePath));
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
                            /*if (this.timeoutTimer != null) {
                                this.timeoutTimer.cancel();
                                this.timeoutTimer = null;
                            }*/
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

    protected Path computeTarget(Path archivePath) {
        return archivePath.getFileName().toString().endsWith(".tar.gz") ?
                Paths.get(archivePath.toString().replace(".tar.gz", "")) :
                Paths.get(archivePath.toString().replace(".zip", ""));
    }

    protected Path extract(EOProduct product, Path archivePath, Path targetPath) throws IOException {
        String satelliteName = product.getSatelliteName();
        final String name = FileUtilities.getFilenameWithoutExtension(archivePath)
                + (satelliteName.equals("Sentinel1") || satelliteName.equals("Sentinel2")
                    ? ".SAFE"
                    : satelliteName.equals("Sentinel3")
                        ? ".SEN3"
                        : "");
        logger.fine(String.format("Begin decompressing %s into %s", name, targetPath));
        Path result;
        if (archivePath.toString().endsWith(".tar.gz")) {
            result = Zipper.decompressTarGz(archivePath, targetPath, true);
            Files.move(result, result.getParent().resolve(name));
        } else {
            result = Zipper.decompressZipMT(archivePath, targetPath, false, true);
        }
        result = result.getParent().resolve(name);
        logger.fine(String.format("Decompression of %s completed",archivePath.getFileName()));
        return result;
    }

    @Override
    protected String getMetadataUrl(EOProduct descriptor) {
        throw new RuntimeException("Metadata file not supported for this strategy");
    }

    @Override
    protected Path findProductPath(Path root, EOProduct product) {
        String location = product.getLocation();
        return location.startsWith(root.toString())
                ? Paths.get(location)
                : super.findProductPath(root, product);
    }
}
