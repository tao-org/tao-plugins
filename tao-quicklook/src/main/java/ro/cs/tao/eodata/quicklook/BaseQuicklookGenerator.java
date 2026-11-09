package ro.cs.tao.eodata.quicklook;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.eodata.OutputDataHandler;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.spi.ServiceRegistryManager;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.FileProcessFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * The abstract class BaseQuicklookGenerator provides a base implementation for generating quicklook images
 * for various file types using the GDAL library or Docker. It offers default configurations for quicklook generation
 * and requires concrete implementations to define the supported file extensions and the inner handling logic.
 *
 * @param <T> The type of item this generator is intended to operate on.
 */
public abstract class BaseQuicklookGenerator<T> implements OutputDataHandler<T> {
    protected static final String gdalDockerImage;
    protected static final boolean useDocker;
    protected static final boolean isDockerStyleVolumeMap;
    protected static final Map<String, String> driverMap = new HashMap<String, String>() {{
        put(".tif", "GTiff"); put(".tiff", "GTiff");
        put(".png", "PNG"); put(".jpg", "JPEG"); put(".bmp", "BMP");
    }};
    private final boolean canCreate;
    protected final Set<String> extensions;
    protected final String extension;
    protected FileProcessFactory factory;

    static {
        ConfigurationProvider configurationManager = ConfigurationManager.getInstance();
        useDocker = "docker".equals(configurationManager.getValue("plugins.use", "")) && DockerHelper.isDockerFound();
        gdalDockerImage = configurationManager.getValue("docker.gdal.image", "geodata/gdal");
        isDockerStyleVolumeMap = SystemUtils.IS_OS_LINUX ||
                                 ConfigurationManager.getInstance()
                                                     .getValue("docker.volume.map.style", "docker")
                                                     .equals("docker");
        Logger.getLogger(GenericQuicklookGenerator.class.getName())
                .fine(String.format("'gdal_translate' will be run %s", useDocker ? "using Docker [" + gdalDockerImage + "]" : "from command line"));
    }

    protected final Logger logger = Logger.getLogger(getClass().getName());

    public BaseQuicklookGenerator() {
        extensions = getSupportedExtensions();
        extension = getExtension();
        canCreate = driverMap.containsKey(extension);
    }

    @Override
    public boolean allowNext() {
        // Only one quicklook handler should be allowed
        return false;
    }

    @Override
    public void setFileProcessFactory(FileProcessFactory factory) {
        this.factory = factory;
    }

    @Override
    public List<T> handle(List<T> list) throws DataHandlingException {
        List<T> results = null;
        if (canCreate) {
            List<T> ret = handleInner(list);
            if (ret != null) {
                results = new ArrayList<>(ret);
            }
        }
        return results;
    }

    /**
     * Retrieves the configured file extension for quicklook files.
     * If no specific configuration is found, the default extension ".png" is returned.
     *
     * @return The configured quicklook file extension as a String.
     */
    protected String getExtension() {
        return ConfigurationManager.getInstance().getValue("quicklook.extension", ".png");
    }

    /**
     * Initializes an {@link Executor} for processing the specified product file path with the provided arguments.
     * If the file extension is not supported, the method returns null.
     *
     * @param productPath the file path of the product to process
     * @param metadata product metadata, if any
     * @param args an array of strings representing arguments to be passed to the process executor
     * @return an {@link Executor} configured to process the specified product file, or null if the file extension is not supported
     * @throws IOException if an I/O exception occurs during initialization
     */
    protected Executor<?> initialize(Path productPath, MetadataInspector.Metadata metadata, String[] args) throws IOException {
        String extension = FileUtilities.getExtension(productPath);
        if (!extensions.contains(extension.toLowerCase())) {
            return null;
        }
        List<String> arguments = new ArrayList<>();
        for (String arg : args) {
            arguments.add(arg.replace("$FULL_PATH", FileUtilities.asUnixPath(productPath, false))
                    .replace("$FOLDER", isDockerStyleVolumeMap
                                        ? FileUtilities.asUnixPath(productPath.getParent(), false)
                                        : productPath.getParent().toAbsolutePath().toString())
                    .replace("$FILE", productPath.getFileName().toString()));
        }
        int idx = arguments.indexOf("-b");
        String bandCount = metadata.getAttribute("bandCount");
        if (idx > 0 && bandCount != null) {
            int count = Integer.parseInt(bandCount);
            if (count > 2) {
                idx += 2;
                arguments.add(idx++, "-b");
                arguments.add(idx++, "2");
                arguments.add(idx++, "-b");
                arguments.add(idx, "3");
            }
        }
        if (this.factory == null) {
            this.factory = FileProcessFactory.createLocal();
        }
        return this.factory.processManager().createExecutor(arguments);
    }

    /**
     * Constructs the GDAL command-line arguments for performing operations on files located on the
     * local file system. This method generates a command-line array that utilizes the GDAL `gdal_translate`
     * tool to process files with a specified output format and scaling options.
     *
     * @return An array of strings representing the GDAL command-line arguments configured to:
     *         - Use the `gdal_translate` utility
     *         - Define the output format and type based on the file extension
     *         - Apply scaling and resizing options
     *         - Process the first band (`-b 1`)
     *         - Define the input and generated output file paths
     */
    protected String[] getGDALCommandLineOnPath() {
        return new String[] {
                "gdal_translate", "-of", driverMap.get(extension), "-ot", "Byte", "-scale", "-outsize", "512", "0", "-b", "1", "$FULL_PATH", "$FULL_PATH" + extension
        };
    }

    /**
     * Constructs the GDAL command-line arguments for performing operations on files using Docker.
     * This method generates a command-line array that utilizes the GDAL `gdal_translate` tool
     * to process files with a specified output format, scaling, and resizing options within
     * a Dockerized environment.
     *
     * @return An array of strings representing the GDAL command-line arguments configured to:
     *         - Use Docker to run the `gdal_translate` utility
     *         - Define the output format and type based on the file extension
     *         - Apply scaling and resizing options
     *         - Process the first band (`-b 1`)
     *         - Specify input and output file paths within the mounted Docker volume
     */
    protected String[] getGDALCommandLineOnDocker() {
        return new String[] {
                "docker", "run", "-t", "--rm", "-v", "$FOLDER:/mnt/data", gdalDockerImage,
                "gdal_translate", "-of", driverMap.get(extension), "-ot", "Byte", "-scale", "-outsize", "512", "0", "-b", "1", "/mnt/data/$FILE", "/mnt/data/$FILE" + extension
        };
    }

    protected MetadataInspector.Metadata getMetadata(Path productPath) {
        Set<MetadataInspector> services = ServiceRegistryManager.getInstance()
                                                                .getServiceRegistry(MetadataInspector.class)
                                                                .getServices();
        if (services != null) {
            MetadataInspector inspector = services.stream()
                                                  .filter(i -> DecodeStatus.INTENDED == i.decodeQualification(productPath))
                                                  .findFirst()
                                                  .orElse(services.stream()
                                                                  .filter(i -> DecodeStatus.SUITABLE == i.decodeQualification(productPath))
                                                                  .findFirst().orElse(null));
            if (inspector != null) {
                try {
                    return inspector.getMetadata(productPath);
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return new MetadataInspector.Metadata();
    }

    /**
     * Retrieves the set of file extensions that are supported for processing.
     *
     * @return a Set of Strings representing supported file extensions handled by the implementation.
     */
    protected abstract Set<String> getSupportedExtensions();

    /**
     * Handles a list of input items and processes them, returning the resulting list of processed items.
     * This method is abstract and must be implemented to define the specific handling logic.
     *
     * @param list the list of items to be processed
     * @return a list of processed items, potentially modified or enhanced based on the implementation
     * @throws DataHandlingException if any error occurs during the processing
     */
    protected abstract List<T> handleInner(List<T> list) throws DataHandlingException;
}
