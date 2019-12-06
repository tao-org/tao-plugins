package ro.cs.tao.datasource.remote.mundi;

import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.SimpleArchiveDownloadStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.products.sentinels.Sentinel1ProductHelper;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Properties;
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
