package ro.cs.tao.docker.gdal;

import ro.cs.tao.docker.DockerVolumeMap;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.enums.ProductStatus;
import ro.cs.tao.eodata.enums.Visibility;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.persistence.EOProductProvider;
import ro.cs.tao.services.model.ItemAction;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.workspaces.RepositoryType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class ConvertToCOGAction implements ItemAction {
    private static final String gdalArgs;
    private static final DockerVolumeMap volumeMap;
    private EOProductProvider productProvider;
    private String user;

    static {
        volumeMap = ExecutionConfiguration.getMasterContainerVolumeMap();
        gdalArgs = "docker run -t --rm -v " + volumeMap.getHostWorkspaceFolder() + ":" + volumeMap.getContainerWorkspaceFolder() +
                " osgeo/gdal:alpine-normal-3.3.2 gdal_translate -of COG -co RESAMPLING=BILINEAR -co COMPRESS=DEFLATE -co TILED=YES %s %s";
    }

    public ConvertToCOGAction() {
    }

    @Override
    public void setProductProvider(EOProductProvider productProvider) {
        this.productProvider = productProvider;
    }

    @Override
    public void setActionUser(String user) {
        this.user = user;
    }

    @Override
    public String name() {
        return "Convert to COG";
    }

    @Override
    public String[] supportedFiles() {
        return new String[] { ".tif", ".tiff", ".nc", ".h5" };
    }

    @Override
    public boolean isIntendedFor(RepositoryType repositoryType) {
        return repositoryType == RepositoryType.LOCAL;
    }

    @Override
    public Path doAction(Path item) throws Exception {
        final String outFile = FileUtilities.getFilenameWithoutExtension(item) + "_cog.tif";
        final Path target = item.getParent().resolve(outFile);
        final List<String> args = Arrays.asList(String.format(gdalArgs,
                                                              volumeMap.relativizePath(FileUtilities.asUnixPath(item, true)),
                                                              volumeMap.relativizePath(FileUtilities.asUnixPath(target, true)))
                                                      .split(" "));
        final Executor<?> executor = Executor.create(ExecutorType.PROCESS, null, args);
        final OutputAccumulator consumer = new OutputAccumulator();
        executor.setOutputConsumer(consumer);
        if (executor.execute(false) != 0) {
            throw new IOException(consumer.getOutput());
        }
        final ServiceRegistry<MetadataInspector> registry = ServiceRegistryManager.getInstance().getServiceRegistry(MetadataInspector.class);
        final Set<MetadataInspector> services = registry.getServices();
        if (services != null) {
            MetadataInspector inspector = services.stream()
                                                  .filter(i -> DecodeStatus.INTENDED == i.decodeQualification(target)).findFirst()
                                                  .orElse(services.stream()
                                                                  .filter(i -> DecodeStatus.SUITABLE == i.decodeQualification(target))
                                                                  .findFirst().orElse(null));
            if (inspector != null) {
                MetadataInspector.Metadata metadata = inspector.getMetadata(target);
                EOProduct product = metadata.toProductDescriptor(target);
                product.setEntryPoint(metadata.getEntryPoint());
                product.addReference(user);
                product.setVisibility(Visibility.PRIVATE);
                product.setAcquisitionDate(metadata.getAquisitionDate());
                if (metadata.getSize() != null) {
                    product.setApproximateSize(metadata.getSize());
                }
                if (metadata.getProductId() != null) {
                    product.setId(metadata.getProductId());
                }
                product.setProductStatus(ProductStatus.PRODUCED);
                productProvider.save(product);
            }
        }
        return target;
    }
}
