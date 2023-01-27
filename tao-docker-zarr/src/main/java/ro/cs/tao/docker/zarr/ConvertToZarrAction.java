package ro.cs.tao.docker.zarr;

import ro.cs.tao.configuration.ConfigurationManager;
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
import ro.cs.tao.utils.DateUtils;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.workspaces.RepositoryType;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConvertToZarrAction implements ItemAction {
    private static final String zarrArgs;
    private static final Pattern pattern = Pattern.compile("((?:[A-Za-z0-9_]*?(?=\\d{8}))((\\d{4})(\\d{2})(\\d{2}))?(?:[A-Za-z0-9_]*))\\.([A-Za-z.]+)");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DockerVolumeMap volumeMap;
    private EOProductProvider productProvider;
    private String user;

    static {
        volumeMap = ExecutionConfiguration.getMasterContainerVolumeMap();
        final String registry = ConfigurationManager.getInstance().getValue("tao.docker.registry");
        zarrArgs = "docker run -t --rm -v " + volumeMap.getHostWorkspaceFolder() + ":" + volumeMap.getContainerWorkspaceFolder() + " " +
                (registry != null ? registry + "/" : "") + "zarr-1-0-0 gdal_to_xarray.py --input %s --out %s --date %s";
    }

    public ConvertToZarrAction() {
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
        return "Convert to Zarr";
    }


    @Override
    public String[] supportedFiles() {
        return new String[] { ".tif", ".tiff", ".nc" };
    }

    public boolean isIntendedFor(RepositoryType repositoryType) {
        return repositoryType == RepositoryType.LOCAL;
    }

    @Override
    public Path doAction(Path item) throws Exception {
        final String outFile = FileUtilities.getFilenameWithoutExtension(item) + ".zarr";
        Matcher matcher = pattern.matcher(outFile);
        LocalDateTime date;
        if (matcher.find()) {
            try {
                date = DateUtils.parseDateTime(matcher.group(2));
            } catch (Exception e) {
                date = DateUtils.parseDate(matcher.group(2)).atStartOfDay();
            }
        } else {
            date = LocalDateTime.now();
        }
        final Path target = item.getParent().resolve(outFile);
        final List<String> args = Arrays.asList(String.format(zarrArgs,
                                                              volumeMap.relativizePath(FileUtilities.asUnixPath(item, true)),
                                                              volumeMap.relativizePath(FileUtilities.asUnixPath(target, true)),
                                                              date.format(formatter)).split(" "));
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
