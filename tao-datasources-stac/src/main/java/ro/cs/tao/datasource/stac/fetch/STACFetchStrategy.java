package ro.cs.tao.datasource.stac.fetch;

import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.stac.STACSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.stac.core.STACClient;
import ro.cs.tao.utils.FileUtilities;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;

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
        final Path target;
        final Set<String> files = product.getFiles();
        if (files != null && files.size() > 0) {
            target = Paths.get(this.destination).resolve(product.getName());
            Files.createDirectories(target);
            STACClient client = this.dataSource.authenticate();
            long size = 0;
            for (String file : files) {
                checkCancelled();
                Path filePath = target.resolve(file.substring(file.lastIndexOf('/') + 1));
                try {
                    logger.fine(String.format("Begin download for %s", file));
                    subActivityStart(filePath.toString());
                    if (Files.exists(filePath)) {
                        Files.delete(filePath);
                    }
                    checkCancelled();
                    client.download(file, target);
                    size += Files.size(filePath);
                    currentProductProgress.add(1);
                    logger.fine(String.format("End download for %s", file));
                } catch (FileNotFoundException fnex) {
                    logger.warning(fnex.getMessage());
                    //filePath = null;
                } catch (InterruptedIOException iioe) {
                    logger.severe("Operation timed out");
                    throw new IOException("Operation timed out");
                } catch (Exception ex) {
                    logger.severe(ex.getMessage());
                    throw new IOException(ex);
                } finally {
                    subActivityEnd(filePath.toString());
                    product.setApproximateSize(size);
                }
                FileUtilities.ensurePermissions(filePath);
            }
        } else {
            target = null;
        }
        return target;
    }

    @Override
    protected String getMetadataUrl(EOProduct descriptor) {
        return null;
    }
}
