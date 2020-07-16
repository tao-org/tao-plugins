package ro.cs.tao.datasource.remote.scihub.download;

import ro.cs.tao.datasource.InterruptedException;
import ro.cs.tao.datasource.remote.scihub.SciHubDataSource;
import ro.cs.tao.datasource.util.Zipper;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.util.ProductHelper;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.products.sentinels.SentinelProductHelper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Sentinel2ArchiveDownloadStrategy extends SentinelDownloadStrategy {

    public Sentinel2ArchiveDownloadStrategy(SciHubDataSource dataSource, String targetFolder) {
        super(dataSource, targetFolder);
    }

    protected Sentinel2ArchiveDownloadStrategy(Sentinel2ArchiveDownloadStrategy other) {
        super(other);
    }

    @Override
    public Sentinel2ArchiveDownloadStrategy clone() { return new Sentinel2ArchiveDownloadStrategy(this); }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException, InterruptedException {
        Path archivePath = super.fetchImpl(product);
        Path productFile;
        if (Boolean.parseBoolean(this.props.getProperty("auto.uncompress", "false"))) {
            productFile = Zipper.decompressZip(archivePath, archivePath.getParent(), true);
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
                    product.setEntryPoint(helper.getMetadataFileName());
                } catch (URISyntaxException e) {
                    logger.severe(e.getMessage());
                }
            }
        } else {
            productFile = archivePath;
        }
        return productFile;
    }

    @Override
    protected Path link(EOProduct product, Path sourceRoot, Path targetRoot) throws IOException {
        Path sourcePath = findProductPath(sourceRoot, product);
        if (sourcePath == null) {
            logger.warning(String.format("Product %s not found in the local archive", product.getName()));
        }
        Path destinationPath = sourcePath != null ? targetRoot.resolve(safetizeName(sourcePath.getFileName())) : null;
        if (destinationPath != null && !Files.exists(destinationPath)) {
            return Files.createSymbolicLink(destinationPath, sourcePath);
        } else {
            return destinationPath;
        }
    }

    private String safetizeName(Path fileName) {
        return fileName.endsWith(".SAFE") ? fileName.toString() : fileName.toString() + ".SAFE";
    }
}
