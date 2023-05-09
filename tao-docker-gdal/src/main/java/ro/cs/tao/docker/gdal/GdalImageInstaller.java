package ro.cs.tao.docker.gdal;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.topology.docker.BaseImageInstaller;

import java.nio.file.Path;

public class GdalImageInstaller extends BaseImageInstaller {
    @Override
    protected String getContainerName() {
        return "gdal-3-3-2";
    }

    @Override
    protected String getDescription() {
        return "GDAL";
    }

    @Override
    protected String getPathInContainer() {
        return "/usr/local/bin";
    }

    @Override
    protected String getPathInSystem() {
        Path path = findInPath(SystemUtils.IS_OS_WINDOWS ? "gdalinfo.exe" : "gdalinfo");
        return path != null ? path.getParent().toString() : null;
    }

    @Override
    protected String getContainerDescriptorFileName() {
        return "gdal_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "gdal_applications.json";
    }

    @Override
    protected String getLogoFileName() {
        return "gdal_logo.png";
    }
}
