package ro.cs.tao.datasource.remote.mundi;

import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.SimpleArchiveDownloadStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.products.sentinels.Sentinel1ProductHelper;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Properties;

public class DownloadStrategy extends SimpleArchiveDownloadStrategy {

    public DownloadStrategy(String targetFolder, Properties properties) {
        super(targetFolder, properties);
        this.fetchMode = FetchMode.OVERWRITE;
    }

    private DownloadStrategy(DownloadStrategy other) {
        super(other);
    }

    @Override
    public String getProductUrl(EOProduct descriptor) {
        URI productUrl = null;
        final String location = descriptor.getLocation();
        String baseUrl = this.props.getProperty("sentinel.download.url", "https://obs.eu-de.otc.t-systems.com/");
        try {
            try {
                URI.create(location);
                // if we get here, the location is a URL
                if (location.startsWith(baseUrl)) {
                    // the location is a MUNDI URL
                    productUrl = new URI(location);
                } else {
                    // the location is not a MUNDI URL, we have to compute one
                    productUrl = new URI(baseUrl + computeRelativeLocation(descriptor).replace(".SAFE","") + ".zip");
                }
            } catch (IllegalArgumentException ignored) {
                // the location is not a URL, it should be relative already
                productUrl = new URI(baseUrl + location);
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
        builder.append(sensingDate.substring(0, 4)).append("/")
                .append(sensingDate.substring(4, 6)).append("/")
                .append(sensingDate.substring(6, 8)).append("/")
                .append(helper.getSensorMode().name()).append("/")
                .append(helper.getPolarisation().name()).append("/").append(descriptor.getName());
        return builder.toString();
    }
}
