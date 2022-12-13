package ro.cs.tao.docker.gdal;

import ro.cs.tao.docker.DockerVolumeMap;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.services.model.ItemAction;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.workspaces.RepositoryType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class ConvertToCOGAction implements ItemAction {
    private static final String gdalArgs;
    private static final DockerVolumeMap volumeMap;

    static {
        volumeMap = ExecutionConfiguration.getMasterContainerVolumeMap();
        gdalArgs = "docker run -t --rm -v " + volumeMap.getHostWorkspaceFolder() + ":" + volumeMap.getContainerWorkspaceFolder() +
                " osgeo/gdal:alpine-normal-3.3.2 gdal_translate -of COG -co RESAMPLING=BILINEAR -co COMPRESS=DEFLATE -co TILED=YES %s %s";
    }

    public ConvertToCOGAction() {
    }

    @Override
    public String name() {
        return "Convert to COG";
    }

    @Override
    public boolean isIntendedFor(RepositoryType repositoryType) {
        return repositoryType == RepositoryType.LOCAL;
    }

    @Override
    public void doAction(Path item) throws Exception {
        final String outFile = FileUtilities.getFilenameWithoutExtension(item) + "_cog.tif";
        final List<String> args = Arrays.asList(String.format(gdalArgs,
                                                              volumeMap.relativizePath(FileUtilities.asUnixPath(item, true)),
                                                              volumeMap.relativizePath(FileUtilities.asUnixPath(item.getParent().resolve(outFile), true)))
                                                      .split(" "));
        final Executor<?> executor = Executor.create(ExecutorType.PROCESS, null, args);
        final OutputAccumulator consumer = new OutputAccumulator();
        executor.setOutputConsumer(consumer);
        if (executor.execute(false) != 0) {
            throw new IOException(consumer.getOutput());
        }
    }
}
