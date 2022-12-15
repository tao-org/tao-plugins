package ro.cs.tao.datasource.stac.fetch;

import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.stac.STACSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.stac.core.STACClient;
import ro.cs.tao.stac.core.model.Asset;
import ro.cs.tao.utils.FileUtilities;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class STACFetchStrategy extends DownloadStrategy<STACClient> {

    public STACFetchStrategy(STACSource dataSource, String targetFolder, Properties properties) {
        super(dataSource, targetFolder, properties);
    }

    protected STACFetchStrategy(STACFetchStrategy other) {
        super(other);
    }

    @Override
    public DownloadStrategy<STACClient> clone() {
        return new STACFetchStrategy(this);
    }

    @Override
    protected Path fetchImpl(EOProduct product) throws IOException {
        final Path target = Paths.get(this.destination).resolve(product.getName());
        Files.createDirectories(target);
        String links = product.getAttributeValue("assets");
        final List<Asset> assets = new ArrayList<>();
        assets.addAll(new ObjectMapper().readerFor(assets.getClass()).readValue(links));
        STACClient client = this.dataSource.authenticate();
        long size = 0;
        for (Asset asset : assets) {
            checkCancelled();
            String link = asset.getHref();
            Path filePath = target.resolve(link.substring(link.lastIndexOf('/')));
            try {
                logger.fine(String.format("Begin download for %s", link));
                subActivityStart(link);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
                checkCancelled();
                long start = System.currentTimeMillis();
                client.download(asset, target);
                size += Files.size(filePath);
                currentProductProgress.add(1);
                logger.fine(String.format("End download for %s", link));
            } catch (FileNotFoundException fnex) {
                logger.warning(fnex.getMessage());
                filePath = null;
            } catch (InterruptedIOException iioe) {
                logger.severe("Operation timed out");
                throw new IOException("Operation timed out");
            } catch (Exception ex) {
                logger.severe(ex.getMessage());
                throw new IOException(ex);
            } finally {
                subActivityEnd(link);
                product.setApproximateSize(size);
            }
            return FileUtilities.ensurePermissions(filePath);
        }
        return target;
    }

    @Override
    protected String getMetadataUrl(EOProduct descriptor) {
        return null;
    }
}
