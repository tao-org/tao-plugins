package ro.cs.tao.docker.sen2cor;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.topology.docker.BaseImageInstaller;

import java.nio.file.Path;

public class Sen2CorImageInstaller extends BaseImageInstaller {
    @Override
    protected String getContainerName() {
        return "sen2cor-2-8";
    }

    @Override
    protected String getDescription() {
        return "Sen2Cor";
    }

    @Override
    protected String getPathInContainer() {
        return "/opt/sen2cor/bin";
    }

    @Override
    protected String getPathInSystem() {
        Path path = findInPath(SystemUtils.IS_OS_WINDOWS ? "L2A_Process.bat" : "L2A_Process");
        return path != null ? path.getParent().toString() : null;
    }

    @Override
    protected String getContainerDescriptorFileName() {
        return "sen2cor_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "sen2cor_operators.json";
    }

    @Override
    protected String getLogoFileName() {
        return null;
    }
}
