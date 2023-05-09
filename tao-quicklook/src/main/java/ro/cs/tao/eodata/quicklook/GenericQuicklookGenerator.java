package ro.cs.tao.eodata.quicklook;

import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.OutputAccumulator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        for (Path productFile : list) {
            try {
                Executor<?> executor = initialize(productFile, useDocker ? getGDALCommandLineOnDocker() : getGDALCommandLineOnPath());
                if (executor != null) {
                    executor.setOutputConsumer(consumer);
                    int code;
                    logger.fine("Trying to create quicklook for " + productFile);
                    if ((code = executor.execute(false)) == 0) {
                        productFile = Paths.get(productFile.toString() + extension);
                    } else {
                        logger.warning(String.format("Quicklook for %s was not created (return code: %d, output: %s)",
                                productFile.getFileName(), code, consumer.getOutput()));
                    }
                } else {
                    logger.warning(String.format("Quicklooks not supported for %s files", FileUtilities.getExtension(productFile)));
                }
            } catch (Exception e) {
                logger.severe(String.format("Cannot create quicklook for %s. Reason: %s", productFile, e.getMessage()));
            }
            results.add(productFile);
        }
        return results.size() == 0 ? null : results;
    }
}
