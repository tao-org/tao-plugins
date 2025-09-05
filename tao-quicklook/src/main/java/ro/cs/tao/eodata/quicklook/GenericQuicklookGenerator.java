package ro.cs.tao.eodata.quicklook;

import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.OutputAccumulator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The GenericQuicklookGenerator class provides an implementation of the BaseQuicklookGenerator for
 * handling the creation of quicklook images for files represented as Path objects. This class supports
 * a variety of file types and operates using the GDAL library, either directly or via Docker.
 */
public class GenericQuicklookGenerator extends BaseQuicklookGenerator<Path> {

    public GenericQuicklookGenerator() {
        super();
    }

    @Override
    public Class<Path> isIntendedFor() { return Path.class; }

    @Override
    public int getPriority() { return 2; }

    @Override
    protected Set<String> getSupportedExtensions() {
        return new HashSet<String>() {{
            add(".tif"); add(".tiff"); add(".nc"); add(".png"); add(".jpg"); add(".bmp");
        }};
    }

    @Override
    protected List<Path> handleInner(List<Path> list) throws DataHandlingException {
        final OutputAccumulator consumer = new OutputAccumulator();
        final List<Path> results = new ArrayList<>();
        for (final Path productFile : list) {
            try {
                MetadataInspector.Metadata metadata = getMetadata(productFile);
                Executor<?> executor = initialize(productFile, metadata, useDocker ? getGDALCommandLineOnDocker() : getGDALCommandLineOnPath());
                if (executor != null) {
                    executor.setOutputConsumer(consumer);
                    executor.onSuccess(() -> {
                        results.add(Paths.get(productFile.toString() + extension));

                    }).onError(() -> {
                        logger.warning(String.format("Quicklook for %s was not created (return code: %d, output: %s)",
                                                     productFile.getFileName(), executor.getReturnCode(), consumer.getOutput()));
                    }).execute(false);
                } else {
                    logger.warning(String.format("Quicklooks not supported for %s files", FileUtilities.getExtension(productFile)));
                }
            } catch (Exception e) {
                logger.severe(String.format("Cannot create quicklook for %s. Reason: %s", productFile, e.getMessage()));
            }
        }
        return results.isEmpty() ? null : results;
    }
}
