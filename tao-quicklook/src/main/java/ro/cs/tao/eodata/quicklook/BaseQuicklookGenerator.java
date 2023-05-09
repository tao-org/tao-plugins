package ro.cs.tao.eodata.quicklook;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.eodata.OutputDataHandler;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.FileProcessFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

public abstract class BaseQuicklookGenerator<T> implements OutputDataHandler<T> {
    protected static final String gdalDockerImage;
    protected static final boolean useDocker;
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

    protected String getExtension() {
        return ConfigurationManager.getInstance().getValue("quicklook.extension", ".png");
    }

    protected Executor<?> initialize(Path productPath, String[] args) throws IOException {
        String extension = FileUtilities.getExtension(productPath);
        if (!extensions.contains(extension.toLowerCase())) {
            return null;
        }
        List<String> arguments = new ArrayList<>();
        for (String arg : args) {
            arguments.add(arg.replace("$FULL_PATH", FileUtilities.asUnixPath(productPath, false))
                    .replace("$FOLDER", FileUtilities.asUnixPath(productPath.getParent(), false))
                    .replace("$FILE", productPath.getFileName().toString()));
        }
        if (this.factory == null) {
            this.factory = FileProcessFactory.createLocal();
        }
        return this.factory.processManager().createExecutor(arguments);
    }

    protected String[] getGDALCommandLineOnPath() {
        return new String[] {
                "gdal_translate", "-of", driverMap.get(extension), "-ot", "Byte", "-scale", "-outsize", "512", "0", "-b", "1", "$FULL_PATH", "$FULL_PATH" + extension
        };
    }

    protected String[] getGDALCommandLineOnDocker() {
        return new String[] {
                "docker", "run", "-t", "--rm", "-v", "$FOLDER:/mnt/data", gdalDockerImage,
                "gdal_translate", "-of", driverMap.get(extension), "-ot", "Byte", "-scale", "-outsize", "512", "0", "-b", "1", "/mnt/data/$FILE", "/mnt/data/$FILE" + extension
        };
    }

    protected abstract Set<String> getSupportedExtensions();
    protected abstract List<T> handleInner(List<T> list) throws DataHandlingException;
}
