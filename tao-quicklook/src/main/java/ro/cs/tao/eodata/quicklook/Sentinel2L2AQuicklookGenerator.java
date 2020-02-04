package ro.cs.tao.eodata.quicklook;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.eodata.OutputDataHandler;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.utils.executors.ProcessExecutor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class Sentinel2L2AQuicklookGenerator implements OutputDataHandler<Path> {
    private static final boolean useDocker;
    private static final String gdalDockerImage;
    private static final String QUICKLOOK_EXTENSION = ".png";
    private static final Set<String> extensions = new HashSet<String>() {{
        add(".jp2"); add(".JP2");
    }};
    private static final String[] gdalOnPathCmd;
    private static final String[] gdalOnDockerCmd;
    private final Logger logger = Logger.getLogger(getClass().getName());

    static {
        ConfigurationProvider configurationManager = ConfigurationManager.getInstance();
        useDocker = Boolean.parseBoolean(configurationManager.getValue("plugins.use.docker", "false")) && DockerHelper.isDockerFound();
        gdalDockerImage = configurationManager.getValue("docker.gdal.image", "geodata/gdal");
        gdalOnPathCmd = new String[] {
                "gdal_translate", "-of", "PNG", "$FULL_PATH", "$FULL_PATH" + QUICKLOOK_EXTENSION
        };
        gdalOnDockerCmd = new String[] {
                "docker", "run", "-t", "--rm", "-v", "$FOLDER:/mnt/data", gdalDockerImage,
                "gdal_translate", "-of", "PNG", "/mnt/data/$FILE", "/mnt/data/$FILE" + QUICKLOOK_EXTENSION
        };
        Logger.getLogger(GenericQuicklookGenerator.class.getName())
                .fine(String.format("'gdal_translate' will be run %s", useDocker ? "using Docker [" + gdalDockerImage + "]" : "from command line"));
    }

    @Override
    public Class<Path> isIntendedFor() { return Path.class; }

    @Override
    public int getPriority() { return 2; }

    @Override
    public List<Path> handle(List<Path> list) throws DataHandlingException {
        final OutputAccumulator consumer = new OutputAccumulator();
        final List<Path> results = new ArrayList<>();
        for (Path productFile : list) {
            final Sentinel2ProductHelper helper;
            try {
                helper = Sentinel2ProductHelper.createHelper(productFile.getFileName().toString());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Path realPath = null;
            try {
                // At least on Windows, docker doesn't handle well folder symlinks in the path
                realPath = FileUtilities.resolveSymLinks(productFile);
                realPath = realPath.resolve("GRANULE");
                realPath = Files.list(realPath).findFirst().orElse(null);
                if (realPath == null) {
                    continue;
                }
                realPath = realPath.resolve("QI_DATA")
                            .resolve("T" + helper.getTileIdentifier() + "_" + helper.getSensingDate() + "_PVI.jp2");
                Executor<?> executor = initialize(realPath, useDocker ? gdalOnDockerCmd : gdalOnPathCmd);
                if (executor != null) {
                    executor.setOutputConsumer(consumer);
                    int code;
                    if ((code = executor.execute(false)) == 0) {
                        realPath = Paths.get(realPath.toString() + QUICKLOOK_EXTENSION);
                    } else {
                        logger.warning(String.format("Quicklook for %s was not created (return code: %d, output: %s)",
                                                     realPath.getFileName(), code, consumer.getOutput()));
                    }
                } else {
                    logger.warning(String.format("Quicklooks not supported for %s files", FileUtilities.getExtension(productFile)));
                }
            } catch (Exception e) {
                logger.severe(String.format("Cannot create quicklook for %s. Reason: %s", productFile, e.getMessage()));
            }
            results.add(realPath);
        }
        return results;
    }

    private Executor<?> initialize(Path productPath, String[] args) throws IOException {
        String extension = FileUtilities.getExtension(productPath);
        if (!extensions.contains(extension.toLowerCase())) {
            return null;
        }
        List<String> arguments = new ArrayList<>();
        for (String arg : args) {
            arguments.add(arg.replace("$FULL_PATH", productPath.toString())
                                  .replace("$FOLDER", productPath.getParent().toString())
                                  .replace("$FILE", productPath.getFileName().toString()));
        }
        try {
            return ProcessExecutor.create(ExecutorType.PROCESS,
                                          InetAddress.getLocalHost().getHostName(),
                                          arguments);
        } catch (UnknownHostException e) {
            throw new IOException(e);
        }
    }
}
