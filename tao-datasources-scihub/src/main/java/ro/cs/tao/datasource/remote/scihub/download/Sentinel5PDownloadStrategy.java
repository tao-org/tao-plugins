package ro.cs.tao.datasource.remote.scihub.download;

import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.utils.CloseableHttpResponse;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Sentinel5PDownloadStrategy extends SentinelDownloadStrategy {

    public Sentinel5PDownloadStrategy(SciHubDataSource dataSource, String targetFolder) {
        super(dataSource, targetFolder);
    }

    protected Sentinel5PDownloadStrategy(Sentinel5PDownloadStrategy other) {
        super(other);
    }

    @Override
    public Sentinel5PDownloadStrategy clone() { return new Sentinel5PDownloadStrategy(this); }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
        FileUtilities.ensureExists(Paths.get(destination));
        String productName = product.getName();
        currentStep = "Tile";
        Path rootPath = Paths.get(destination, productName + ".nc");
        if (this.currentProduct == null) {
            this.currentProduct = product;
        }
        final String statusUrl = getProductOnlineStatusUrl(product);
        if (statusUrl != null) {
            try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, statusUrl, this.credentials)) {
                int statusCode = response.getStatusLine().getStatusCode();
                logger.finest(String.format("GET %s received status code %d and content", statusUrl, statusCode));
                switch (statusCode) {
                    case 200:
                        final String body = EntityUtils.toString(response.getEntity());
                        logger.finest(String.format("Content: %s", body));
                        if (!Boolean.parseBoolean(body)) {
                            throw new IOException(String.format("Product %s is not online", productName));
                        }
                        break;
                    case 401:
                        throw new IOException("The supplied credentials are invalid or the user is unauthorized");
                    default:
                        throw new IOException(String.format("The request was not successful. Reason: %s",
                                response.getStatusLine().getReasonPhrase()));
                }
            }
        }
        final String productURL = getProductUrl(product);
        final String token = this.dataSource.authenticate();
        if (product.getApproximateSize() < 1) {
            updateProductApproximateSize(product, productURL, token);
        }
        return downloadFile(productURL, rootPath, token);
    }
}
