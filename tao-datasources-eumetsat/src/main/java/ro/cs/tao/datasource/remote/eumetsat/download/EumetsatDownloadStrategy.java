package ro.cs.tao.datasource.remote.eumetsat.download;

import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.FetchMode;
import ro.cs.tao.datasource.remote.eumetsat.EumetsatDataSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.opensearch.OpenSearchClient;
import ro.cs.tao.opensearch.parser.EOPMetadataParser;
import ro.cs.tao.opensearch.parser.PostDownloadAction;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.Zipper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Pattern;

public class EumetsatDownloadStrategy extends DownloadStrategy<Void> {

    private static final Pattern extensionPattern = Pattern.compile("\\.(\\w{2,3}|\\w{1,2}\\d{1})$");

    public EumetsatDownloadStrategy(EumetsatDataSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
        this.fetchMode = FetchMode.OVERWRITE;
    }

    public EumetsatDownloadStrategy(EumetsatDownloadStrategy other) {
        super(other);
        this.fetchMode = FetchMode.OVERWRITE;
    }

    @Override
    public EumetsatDownloadStrategy clone() {
        return new EumetsatDownloadStrategy(this);
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException {
        Path productFile = null;
        try {
            this.dataSource.authenticate();
            subActivityStart(product.getName());
            final String archiveName = StringUtilities.replaceSpecialCharacters(getFileName(product.getLocation()));
            Path archivePath = Paths.get(destination, archiveName);
            FileUtilities.ensureExists(Paths.get(destination));
            Files.deleteIfExists(archivePath);
            final OpenSearchClient openSearchClient = ((EumetsatDataSource) this.dataSource).getClient();
            openSearchClient.setPostDownloadAction(new MetadataResolver());
            openSearchClient.download(product, archivePath);
            logger.fine(String.format("End download for %s", product.getName()));
            final boolean isArchive = archiveName.endsWith(".zip") || archiveName.endsWith(".tar.gz");

            if (isArchive && Boolean.parseBoolean(this.props.getProperty("auto.uncompress", "true"))) {
                productFile = extract(archivePath, computeTarget(archivePath));
            } else {
                productFile = archivePath;
            }
            if (productFile != null) {
                try {
                    product.setLocation(productFile.toUri().toString());
                } catch (URISyntaxException e) {
                    logger.severe(e.getMessage());
                }
            }
        } finally {
            subActivityEnd(product.getName());
        }
        return productFile;
    }

    @Override
    protected String getMetadataUrl(EOProduct descriptor) {
        return "";
    }

    protected Path computeTarget(Path archivePath) {
        return super.computeTarget(archivePath);
    }

    protected Path extract(Path archivePath, Path targetPath) throws IOException {
        logger.fine(String.format("Begin decompressing %s into %s", archivePath.getFileName(), targetPath));
        Path result = archivePath.toString().endsWith(".tar.gz") ?
                Zipper.decompressTarGz(archivePath, targetPath, true) :
                Zipper.decompressZip(archivePath, targetPath, true);
        logger.fine(String.format("Decompression of %s completed", archivePath.getFileName()));
        return result;
    }

    protected String getFileName(String url) {
        String name = url.substring(url.lastIndexOf("/") + 1);
        return !extensionPattern.matcher(name).find() ? name + ".zip" : name;
    }

    static class MetadataResolver implements PostDownloadAction {

        @Override
        public void execute(InputStream stream, EOProduct product) throws Exception {
            new EOPMetadataParser().parse(stream, product);
        }
    }
}
