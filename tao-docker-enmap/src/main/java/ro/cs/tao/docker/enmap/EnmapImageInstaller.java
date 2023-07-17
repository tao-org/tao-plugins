package ro.cs.tao.docker.enmap;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.topology.docker.BaseImageInstaller;

import java.nio.file.Path;

public class EnmapImageInstaller extends BaseImageInstaller {
    @Override
    public String getContainerName() {
        return "enmap";
    }

    @Override
    protected String getDescription() {
        return "EnMAP-Box";
    }

    @Override
    protected String getPathInContainer() {
        return "/usr/local/bin";
    }

    @Override
    protected String getPathInSystem() {
        Path path = findInPath(SystemUtils.IS_OS_WINDOWS ? "qgis_process-qgis.bat" : "qgis_process");
        return path != null ? path.getParent().toString() : null;
    }

    @Override
    protected String getContainerDescriptorFileName() {
        return "enmap_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "enmap_applications.json";
    }

    @Override
    protected String getLogoFileName() {
        return "enmap_logo.png";
    }

    @Override
    protected String[] additionalResources() {
        return new String[] { "enmap-box.zip" };
    }
}
