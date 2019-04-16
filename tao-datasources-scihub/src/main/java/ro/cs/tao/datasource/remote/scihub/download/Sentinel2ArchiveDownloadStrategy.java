package ro.cs.tao.datasource.remote.scihub.download;

import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.remote.ProductHelper;
import ro.cs.tao.datasource.util.Zipper;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.products.sentinels.SentinelProductHelper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Sentinel2ArchiveDownloadStrategy extends SentinelDownloadStrategy {

    public Sentinel2ArchiveDownloadStrategy(String targetFolder) {
        super(targetFolder);
    }

    protected Sentinel2ArchiveDownloadStrategy(Sentinel2ArchiveDownloadStrategy other) {
        super(other);
    }

    @Override
    public Sentinel2ArchiveDownloadStrategy clone() { return new Sentinel2ArchiveDownloadStrategy(this); }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
        Path archivePath = super.fetchImpl(product);
        Path productFile = Zipper.decompressZip(archivePath, archivePath.getParent(), true);
        if (productFile != null) {
            try {
                if (!productFile.toString().contains(product.getName())) {
                    productFile = Paths.get(archivePath.toString().replace(".zip", ".SAFE"));
                }
                product.setLocation(productFile.toUri().toString());
                ProductHelper helper = SentinelProductHelper.create(product.getName());
                if (helper instanceof Sentinel2ProductHelper) {
                    Sentinel2ProductHelper s2Helper = (Sentinel2ProductHelper) helper;
                    product.addAttribute("tiles", s2Helper.getTileIdentifier());
                }
            } catch (URISyntaxException e) {
                logger.severe(e.getMessage());
            }
        }
        return productFile;
    }
}
