package ro.cs.tao.datasource.remote.mundi;

import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.SimpleArchiveDownloadStrategy;
import ro.cs.tao.eodata.EOProduct;

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
        URI location = null;
        try {
            String baseUrl = this.props.getProperty("sentinel.download.url", "https://obs.eu-de.otc.t-systems.com/");
            location = new URI(baseUrl + descriptor.getLocation());
        } catch (URISyntaxException ignored) {
        }
        return location != null ? location.toString() : null;
    }

    @Override
    public SimpleArchiveDownloadStrategy clone() {
        return new DownloadStrategy(this);
    }

    @Override
    protected Path computeTarget(Path archivePath) {
        return archivePath.getParent();
    }
}
