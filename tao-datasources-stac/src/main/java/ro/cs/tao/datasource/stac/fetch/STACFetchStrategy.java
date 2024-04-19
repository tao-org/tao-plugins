package ro.cs.tao.datasource.stac.fetch;

import ro.cs.tao.datasource.DataSourceConfiguration;
import ro.cs.tao.datasource.persistence.DataSourceConfigurationProvider;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.stac.STACSource;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.services.factory.StorageServiceFactory;
import ro.cs.tao.services.interfaces.StorageService;
import ro.cs.tao.stac.core.STACClient;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.workspaces.Repository;
import ro.cs.tao.workspaces.RepositoryType;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
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
        if (files != null && !files.isEmpty()) {
            target = Paths.get(this.destination).resolve(product.getName());
            Files.createDirectories(target);
            STACClient client = this.dataSource.authenticate();;
            client.setProgressListener(this.progressListener);
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
                    if (file.startsWith("http")) {
                        client.download(file, target);
                    } else {
                        final Repository repository = getRepository();
                        if (repository != null) {
                            final StorageService<?, ?> service = StorageServiceFactory.getInstance(repository);
                            if (service != null) {
                                String relativePath = file.replace(repository.getUrlPrefix() + "://", "");
                                relativePath = relativePath.substring(relativePath.indexOf('/') + 1);
                                final Object input = service.download(relativePath);
                                if (InputStream.class.isAssignableFrom(input.getClass())) {
                                    Files.copy((InputStream) input, filePath, StandardCopyOption.REPLACE_EXISTING);
                                }
                            }
                        }
                    }
                    size += Files.exists(filePath) ? Files.size(filePath) : 0;
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

    private Repository getRepository() {
        final DataSourceConfigurationProvider provider = ((STACSource) this.dataSource).getConfigurationProvider();
        if (provider != null) {
            final DataSourceConfiguration configuration = provider.get(this.dataSource.getId());
            if (configuration != null) {
                final Repository repository = new Repository();
                repository.setName(this.dataSource.getId());
                repository.setParameters(new LinkedHashMap<>(configuration.getParameters()));
                repository.setType(RepositoryType.valueOf(configuration.getParameters().get("type")));
                repository.setEditable(false);
                repository.setReadOnly(true);
                repository.setSystem(false);
                repository.setUrlPrefix(repository.getType().prefix());
                return repository;
            }
        }
        return null;
    }
}
