package ro.cs.tao.docker.senet;

import ro.cs.tao.docker.Application;
import ro.cs.tao.topology.docker.BaseImageInstaller;

public class SenETImageInstaller extends BaseImageInstaller {

    @Override
    protected String getContainerName() { return "senet-1-0-0"; }

    @Override
    protected String getDescription() { return "SenET"; }

    @Override
    protected String getPathInContainer() { return "/opt/senet"; }

    @Override
    protected String getPathInSystem() {
        return null;
    }

    @Override
    protected String getContainerDescriptorFileName() {
        return "senet_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "senet_operators.json";
    }

    @Override
    protected String getLogoFileName() {
        return "senet_logo.png";
    }

    @Override
    protected String[] additionalResources() {
        return new String[] { "update_snap.sh", "senet_scripts.zip" };
    }

    @Override
    protected void configureApplication(Application app) {
        if (app.getPath() == null) {
            app.setPath("senET.sh");
        }
    }
}

