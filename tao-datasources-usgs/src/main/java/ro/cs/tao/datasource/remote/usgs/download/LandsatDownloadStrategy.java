package ro.cs.tao.datasource.remote.usgs.download;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.usgs.USGSDataSource;
import ro.cs.tao.datasource.util.HttpMethod;
import ro.cs.tao.datasource.util.NetUtils;
import ro.cs.tao.datasource.util.Zipper;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.utils.FileUtils;

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
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class LandsatDownloadStrategy extends DownloadStrategy {
    private static final Properties properties;
    private static final String LOGIN_REQUEST = "login?jsonRequest={\"username\":\"%s\",\"password\":\"%s.\",\"authType\":\"EROS\",\"catalogId\":\"EE\"}";

    static {
        properties = new Properties();
        try {
            properties.load(USGSDataSource.class.getResourceAsStream("usgs.properties"));
        } catch (IOException ignored) {
        }
    }

    private String csrfToken;
    private String ncFormInfo;

    public LandsatDownloadStrategy(String targetFolder) {
        super(targetFolder, properties);
    }

    @Override
    protected String getMetadataUrl(EOProduct descriptor) {
        throw new RuntimeException("Metadata file not supported for this strategy");
    }

    @Override
    public Path fetch(EOProduct product) throws IOException, InterruptedException {
        checkCancelled();
        String tileId = "";
        if (this.filteredTiles != null) {
            Matcher matcher = tileIdPattern.matcher(product.getName());
            if (!matcher.matches()) {
                return null;
            }
            if (matcher.groupCount() == 1) {
                // group(0) contains whole matched string and group(1) is actually the group we want
                tileId = matcher.group(1);
            }
        }
        doAuthenticate();
        checkCancelled();
        Path productFile;
        if (currentProduct == null) {
            currentProduct = product;
            currentProductProgress = 0;
        }
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, getProductUrl(product), null)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    markStart(product.getName());
                    Path archivePath = Paths.get(destination, product.getName() + ".tar.gz");
                    FileUtils.ensureExists(Paths.get(destination));
                    Files.deleteIfExists(archivePath);
                    InputStream inputStream = response.getEntity().getContent();
                    SeekableByteChannel outputStream = null;
                    currentProduct.setApproximateSize(response.getEntity().getContentLength());
                    try {
                        outputStream = Files.newByteChannel(archivePath, EnumSet.of(StandardOpenOption.CREATE,
                                                                                 StandardOpenOption.APPEND,
                                                                                 StandardOpenOption.WRITE));
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                        int totalRead = 0;
                        logger.fine("Begin reading from input stream");
                        while (!isCancelled() && (read = inputStream.read(buffer)) != -1) {
                            outputStream.write(ByteBuffer.wrap(buffer, 0, read));
                            totalRead += read;
                            currentProductProgress = Math.min(1.0, currentProduct.getApproximateSize() > 0 ?
                                    (double) totalRead / (double) currentProduct.getApproximateSize() : 0);
                        }
                        outputStream.close();
                        logger.fine("End reading from input stream");
                        checkCancelled();
                        productFile = Zipper.decompressTarGz(archivePath,
                                                                  Paths.get(archivePath.toString().replace(".tar.gz", "")),
                                                                  true);
                        if (productFile != null) {
                            try {
                                product.setLocation(productFile.toUri().toString());
                                product.addAttribute("tiles", new StringBuilder("{")
                                        .append(tileId)
                                        .append("}").toString());
                            } catch (URISyntaxException e) {
                                logger.severe(e.getMessage());
                            }
                        }
                    } finally {
                        if (outputStream != null && outputStream.isOpen()) outputStream.close();
                        if (inputStream != null) inputStream.close();
                    }
                    logger.fine(String.format("End download for %s", product.getLocation()));
                    break;
                case 401:
                    throw new QueryException("The supplied credentials are invalid!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                                                           response.getStatusLine().getReasonPhrase()));
            }
        } finally {
            markEnd(product.getName());
        }
        return productFile;
    }

    private void doAuthenticate() throws IOException {
        String authUrl = properties.getProperty("usgs.auth.url");
        if (authUrl == null) {
            throw new MissingResourceException("Authentication url not configured",
                                               USGSDataSource.class.getSimpleName(),
                                               "usgs.auth.url");
        }
        if (csrfToken == null) {
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, authUrl, null)) {
                switch (response.getStatusLine().getStatusCode()) {
                    case 200:
                        String body = EntityUtils.toString(response.getEntity());
                        String toFind = "name=\"csrf_token\" ";
                        int idx = body.indexOf(toFind) + toFind.length();
                        int start = body.indexOf("\"", idx) + 1;
                        int end = body.indexOf("\"", start + 1);
                        csrfToken = body.substring(start, end);
                        toFind = "name=\"__ncforminfo\" ";
                        idx = body.indexOf(toFind) + toFind.length();
                        start = body.indexOf("\"", idx) + 1;
                        end = body.indexOf("\"", start + 1);
                        ncFormInfo = body.substring(start, end);
                        break;
                    case 401:
                        throw new QueryException("The supplied credentials are invalid!");
                    default:
                        throw new QueryException(String.format("The request was not successful. Reason: %s",
                                                               response.getStatusLine().getReasonPhrase()));
                }
            }
        }
        List<NameValuePair> postParams = new ArrayList<>();
        postParams.add(new BasicNameValuePair("username", this.credentials.getUserName()));
        postParams.add(new BasicNameValuePair("password", this.credentials.getPassword()));
        postParams.add(new BasicNameValuePair("csrf_token", csrfToken));
        postParams.add(new BasicNameValuePair("__ncforminfo", ncFormInfo));

        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.POST, authUrl + "/login", this.credentials, postParams)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                case 302:
                    break;
                case 401:
                    throw new QueryException("The supplied credentials are invalid!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                                                           response.getStatusLine().getReasonPhrase()));
            }
        }
    }
}
