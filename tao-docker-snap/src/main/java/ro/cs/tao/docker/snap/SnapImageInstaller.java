package ro.cs.tao.docker.snap;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.docker.Application;
import ro.cs.tao.topology.docker.BaseImageInstaller;

import java.nio.file.Path;

public abstract class SnapImageInstaller extends BaseImageInstaller {

    @Override
    protected String getDescription() { return "SNAP"; }

    @Override
    protected String getPathInContainer() { return "/opt/snap/bin"; }

    @Override
    protected String getPathInSystem() {
        Path path = findInPath(SystemUtils.IS_OS_WINDOWS ? "gpt.exe" : "gpt");
        return path != null ? path.getParent().toString() : null;
    }

    @Override
    protected void configureApplication(Application app) {
        if (app.getPath() == null) {
            app.setPath("gpt");
        }
        app.setParallelFlagTemplate("-q <integer>");
    }

    @Override
    protected String[] additionalResources() {
        // In case of a path, classpath resource paths must always use forward slashes (/), not the system-specific file separator (which can be \ on Windows or / on Unix-like systems)
        return new String[] { "update_snap.sh", "graphs/grd-tc.xml" };
    }
}
