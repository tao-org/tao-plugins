package ro.cs.tao.eodata.actions;

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
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public class ExtractStatisticsAction implements ItemAction {
    private static final String gdalArgs;
    private static final DockerVolumeMap volumeMap;

    static {
        volumeMap = ExecutionConfiguration.getMasterContainerVolumeMap();
        gdalArgs = "docker run -t --rm -v " + volumeMap.getHostWorkspaceFolder() + ":" + volumeMap.getContainerWorkspaceFolder() +
                   " osgeo/gdal:alpine-normal-3.3.2 gdalinfo -json -stats -hist %s";
    }

    @Override
    public String name() {
        return "Extract Statistics";
    }

    @Override
    public boolean isIntendedFor(RepositoryType repositoryType) {
        return RepositoryType.LOCAL.equals(repositoryType);
    }

    @Override
    public String[] supportedFiles() {
        return new String[] { ".tif", ".tiff", ".nc", ".jp2" };
    }

    @Override
    public boolean canBeUsedWith(Repository repository) {
        return repository != null && repository.getType() == RepositoryType.LOCAL;
    }

    @Override
    public Path doAction(Path item) throws Exception {
        if (Files.isDirectory(item)) {
            // gdalinfo would work only on files
            return null;
        }
        Path target = null;
        final List<String> args = Arrays.asList(String.format(gdalArgs,
                                                              volumeMap.relativizePath(FileUtilities.asUnixPath(item, true)))
                                                      .split(" "));
        final Executor<?> executor = Executor.create(ExecutorType.PROCESS, null, args);
        final OutputAccumulator consumer = new OutputAccumulator();
        executor.setOutputConsumer(consumer);
        if (executor.execute(false) != 0) {
            throw new IOException(consumer.getOutput());
        } else {
            target = Paths.get(item.toAbsolutePath().toString() + ".stats.json");
            Files.writeString(target, consumer.getOutput(), StandardOpenOption.TRUNCATE_EXISTING);
        }
        return target;
    }
}
