package ro.cs.tao.datasource.remote.scihub.download;

import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.datasource.util.Zipper;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.products.sentinels.Sentinel3ProductHelper;
import ro.cs.tao.products.sentinels.SentinelProductHelper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Sentinel3DownloadStrategy extends SentinelDownloadStrategy {

    public Sentinel3DownloadStrategy(SciHubDataSource dataSource, String targetFolder) {
        super(dataSource, targetFolder);
    }

    protected Sentinel3DownloadStrategy(Sentinel3DownloadStrategy other) {
        super(other);
    }

    @Override
    public Sentinel3DownloadStrategy clone() { return new Sentinel3DownloadStrategy(this); }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
        Path archivePath = super.fetchImpl(product);
        Path productFile;
        if (Boolean.parseBoolean(this.props.getProperty("auto.uncompress", "false"))) {
            productFile = Zipper.decompressZip(archivePath, archivePath.getParent(), true);
            if (productFile != null) {
                try {
                    if (!productFile.toString().contains(product.getName())) {
                        productFile = Paths.get(archivePath.toString().replace(".zip", ".SEN3"));
                    }
                    product.setLocation(productFile.toUri().toString());
                    ProductHelper helper = SentinelProductHelper.create(product.getName());
                    if (helper instanceof Sentinel3ProductHelper) {
                        Sentinel3ProductHelper s3Helper = (Sentinel3ProductHelper) helper;
                        product.addAttribute("tiles", s3Helper.getOrbit());
                    }
                } catch (URISyntaxException e) {
                    logger.severe(e.getMessage());
                }
            }
        } else {
            productFile = archivePath;
        }
        return productFile;
    }
}
