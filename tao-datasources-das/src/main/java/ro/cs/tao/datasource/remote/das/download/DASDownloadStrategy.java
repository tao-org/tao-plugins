package ro.cs.tao.datasource.remote.das.download;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import ro.cs.tao.datasource.ProductHelperFactory;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.das.DASDataSource;
import ro.cs.tao.datasource.remote.das.common.Token;
import ro.cs.tao.datasource.util.Zipper;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
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

public class DASDownloadStrategy extends DownloadStrategy<Token> {
    private Token apiKey;
    private long lastRead;

    public DASDownloadStrategy(DASDataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
        this.fetchMode = FetchMode.OVERWRITE;
        this.lastRead = 0;
    }

    protected DASDownloadStrategy(DASDownloadStrategy other) {
        super(other);
        this.fetchMode = FetchMode.OVERWRITE;
        this.apiKey = other.apiKey;
        this.lastRead = 0;
    }

    @Override
    public DASDownloadStrategy clone() {
        return new DASDownloadStrategy(this);
    }

    @Override
    public void setAuthentication(Token authentication) {
        this.apiKey = authentication;
    }

    @Override
    public String getProductUrl(EOProduct descriptor) {
        String location = descriptor.getLocation();
        return location != null ? location : this.dataSource.getConnectionString("Download") + "(" + descriptor.getId() + ")/$value";
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException {
        checkCancelled();
        Path productFile = null;
        if (currentProduct == null) {
            currentProduct = product;
        }
        if (this.apiKey == null) {
            this.apiKey = this.dataSource.authenticate();
        }
        String productUrl = getProductUrl(product);
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader("Authorization", "Bearer " + this.apiKey.getAccessToken()));
        if (this.lastRead > 0) {
            headers.add(new BasicHeader("Range", "bytes=" + lastRead + "-"));
        }
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, productUrl, headers, null, 30000)) {
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
                        if (this.lastRead == 0) {
                            Files.deleteIfExists(archivePath);
                        }
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
                            while (!isCancelled() && (read = inputStream.read(buffer)) != -1) {
                                outputStream.write(ByteBuffer.wrap(buffer, 0, read));
                                lastRead += read;
                                currentProductProgress.add(read);
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
                        } catch (SocketTimeoutException ex) {
                            productFile = fetchImpl(product);
                        } finally {
                            if (outputStream != null && outputStream.isOpen()) outputStream.close();
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
        String satelliteName = product.getSatelliteName().replace("-", "");
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
            result = result.getParent().resolve(name);
        } else if (archivePath.toString().endsWith(".zip")){
            result = Zipper.decompressZipMT(archivePath, targetPath, false, true);
            result = result.getParent().resolve(name);
        } else {
            result = targetPath;
        }
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
