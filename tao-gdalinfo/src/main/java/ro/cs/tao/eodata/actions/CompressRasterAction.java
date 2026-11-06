package ro.cs.tao.eodata.actions;

import ro.cs.eo.gdal.dataio.drivers.*;
import ro.cs.tao.docker.DockerVolumeMap;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.services.model.ItemAction;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.workspaces.Repository;
import ro.cs.tao.workspaces.RepositoryType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of the {@link ItemAction} interface for compressing raster files.
 * This action is specifically intended to process raster datasets using GDAL utilities
 * for compression purposes. The compression uses the DEFLATE algorithm, along with
 * additional optimizations like tiling and pixel type detection (predictor).
 *
 * @author  Cosmin Cara
 * @since   1.6.1
 */
public class CompressRasterAction implements ItemAction {
    private static final String gdalArgs;
    private static final DockerVolumeMap volumeMap;

    static {
        volumeMap = ExecutionConfiguration.getMasterContainerVolumeMap();
        gdalArgs = "docker run -t --rm -v " + volumeMap.getHostWorkspaceFolder() + ":" + volumeMap.getContainerWorkspaceFolder() +
                   " osgeo/gdal:alpine-normal-3.3.2 gdal_translate -co COMPRESS=DEFLATE -co TILED=YES -co PREDICTOR=%d %s %s";
    }

    @Override
    public String name() {
        return "Compress Raster";
    }

    @Override
    public boolean isIntendedFor(RepositoryType repositoryType) {
        return RepositoryType.LOCAL.equals(repositoryType);
    }

    @Override
    public boolean canBeUsedWith(Repository repository) {
        return repository != null && repository.getType() == RepositoryType.LOCAL;
    }

    @Override
    public String[] supportedFiles() {
        return new String[] { ".tif", ".tiff" };
    }

    @Override
    public Path doAction(Path item) throws Exception {
        if (Files.isDirectory(item)) {
            // gdalinfo would work only on files
            return null;
        }
        Path target = null;
        GDAL.useExceptions();
        final Dataset gdalDataset = GDAL.open(item.toAbsolutePath().toString(), GDALConst.gaReadonly());
        if (gdalDataset == null) {
            throw new IllegalStateException("Fail to open Dataset for file: " + item + ". " + GDAL.getLastErrorMsg());
        }
        int rasterCount = gdalDataset.getRasterCount();
        if (rasterCount > 0) {
            Band gdalBand = gdalDataset.getRasterBand(1);
            int gdalDataType = gdalBand.getDataType();
            int predictor;
            if (GDALConstConstants.gdtByte() == gdalDataType || GDALConstConstants.gdtUint16() == gdalDataType ||
                GDALConstConstants.gdtCInt16() == gdalDataType || GDALConstConstants.gdtInt16() == gdalDataType ||
                GDALConstConstants.gdtUint32() == gdalDataType || GDALConstConstants.gdtCInt32() == gdalDataType ||
                GDALConstConstants.gdtInt32() == gdalDataType) {
                predictor = 2;
            } else {
                predictor = 3;
            }
            final String outFile = FileUtilities.getFilenameWithoutExtension(item) + "_compressed" + FileUtilities.getExtension(item);
            target = item.getParent().resolve(outFile);
            final List<String> args = Arrays.asList(String.format(gdalArgs,
                                                                  predictor,
                                                                  volumeMap.relativizePath(FileUtilities.asUnixPath(item)),
                                                                  volumeMap.relativizePath(FileUtilities.asUnixPath(target)))
                                                          .split(" "));
            final Executor<?> executor = Executor.create(ExecutorType.PROCESS, null, args);
            final OutputAccumulator consumer = new OutputAccumulator();
            executor.setOutputConsumer(consumer);
            if (executor.execute(false) != 0) {
                throw new IOException(consumer.getOutput());
            }
        }
        return target;
    }
}
