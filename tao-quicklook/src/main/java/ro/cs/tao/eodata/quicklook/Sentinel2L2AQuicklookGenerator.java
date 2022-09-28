package ro.cs.tao.eodata.quicklook;

import ro.cs.tao.eodata.DataHandlingException;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.OutputAccumulator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Sentinel2L2AQuicklookGenerator extends BaseQuicklookGenerator<Path> {

    public Sentinel2L2AQuicklookGenerator() {
        super();
    }

    @Override
    public Class<Path> isIntendedFor() { return Path.class; }

    @Override
    public int getPriority() { return 1; }

    @Override
    protected Set<String> getSupportedExtensions() {
        return new HashSet<String>() {{ add(".jp2"); add(".JP2"); }};
    }

    @Override
    protected String[] getGDALCommandLineOnPath() {
        return new String[] {
                "gdal_translate", "-of", driverMap.get(extension), "$FULL_PATH", "$FULL_PATH" + extension
        };
    }

    @Override
    protected String[] getGDALCommandLineOnDocker() {
        return new String[] {
                "docker", "run", "-t", "--rm", "-v", "$FOLDER:/mnt/data", gdalDockerImage,
                "gdal_translate", "-of", driverMap.get(extension), "/mnt/data/$FILE", "/mnt/data/$FILE" + extension
        };
    }

    @Override
    protected List<Path> handleInner(List<Path> list) throws DataHandlingException {
        final OutputAccumulator consumer = new OutputAccumulator();
        List<Path> results = new ArrayList<>();
        for (Path productFile : list) {
            final Sentinel2ProductHelper helper;
            try {
                helper = Sentinel2ProductHelper.createHelper(productFile.getFileName().toString());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            try {
                if (Files.exists(productFile.resolve(productFile.getFileName().toString().replace(".SAFE", "-ql.jpg")))) {
                    productFile = productFile.resolve(productFile.getFileName().toString().replace(".SAFE", "-ql.jpg"));
                } else {
                    // At least on Windows, docker doesn't handle well folder symlinks in the path
                    productFile = productFile.resolve("GRANULE");
                    productFile = this.factory.fileManager().list(productFile).stream().findFirst().orElse(null);
                    if (productFile == null) {
                        continue;
                    }
                    productFile = productFile.resolve("QI_DATA")
                                             .resolve("T" + helper.getTileIdentifier() + "_" + helper.getSensingDate() + "_PVI.jp2");
                    Executor<?> executor = initialize(productFile, useDocker ? getGDALCommandLineOnDocker() : getGDALCommandLineOnPath());
                    if (executor != null) {
                        executor.setOutputConsumer(consumer);
                        int code;
                        if ((code = executor.execute(false)) == 0) {
                            productFile = Paths.get(productFile + extension);
                        } else {
                            logger.warning(String.format("Quicklook for %s was not created (return code: %d, output: %s)",
                                                         productFile.getFileName(), code, consumer.getOutput()));
                        }
                    } else {
                        logger.warning(String.format("Quicklooks not supported for %s files", FileUtilities.getExtension(productFile)));
                    }
                }
            } catch (Exception e) {
                logger.severe(String.format("Cannot create quicklook for %s. Reason: %s", productFile, e.getMessage()));
            }
            results.add(productFile);
        }
        return results.size() == 0 ? null : results;
    }

}
