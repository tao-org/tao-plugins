package ro.cs.tao.eodata.quicklook;

import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.eodata.OutputDataHandler;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.DebugOutputConsumer;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.ProcessExecutor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class GenericQuicklookGenerator implements OutputDataHandler<Path> {
    private static final String QUICKLOOK_EXTENSION = ".png";
    private static final Set<String> extensions = new HashSet<String>() {{
        add(".tif"); add(".tiff"); add(".nc"); add(".png"); add(".jpg"); add(".bmp");
    }};
    private static final String[] gdalOnPathCmd = new String[] {
            "gdal_translate", "-of", "PNG", "-ot", "Byte", "-scale", "-outsize", "512", "0", "-b", "1", "$FULL_PATH", "$FULL_PATH" + QUICKLOOK_EXTENSION
    };
    private static final String[] gdalOnDocker = new String[] {
            "docker", "run", "-t", "--rm", "-v", "$FOLDER:/mnt/data", "geodata/gdal",
            "gdal_translate", "-of", "PNG", "-ot", "Byte", "-scale", "-outsize", "512", "0", "-b", "1", "/mnt/data/$FILE", "/mnt/data/$FILE" + QUICKLOOK_EXTENSION
    };
    private final Logger logger = Logger.getLogger(getClass().getName());
    @Override
    public Class<Path> isIntendedFor() { return Path.class; }

    @Override
    public int getPriority() { return 1; }

    @Override
    public List<Path> handle(List<Path> list) throws DataHandlingException {
        final DebugOutputConsumer consumer = new DebugOutputConsumer();
        final List<Path> results = new ArrayList<>();
        for (Path productFile : list) {
            Path realPath = null;
            try {
                // At least on Windows, docker doesn't handle well folder symlinks in the path
                realPath = FileUtilities.resolveSymLinks(productFile);
                Executor executor = initialize(realPath, DockerHelper.isDockerFound() ? gdalOnDocker : gdalOnPathCmd);
                if (executor != null) {
                    executor.setOutputConsumer(consumer);
                    executor.execute(false);
                    realPath = Paths.get(realPath.toString() + ".png");
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

    private Executor initialize(Path productPath, String[] args) throws IOException {
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
