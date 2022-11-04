package ro.cs.tao.docker.ba;

import ro.cs.tao.topology.docker.BaseImageInstaller;

public class BAImageInstaller extends BaseImageInstaller {
    @Override
    protected String getContainerName() {
        return "ba-22-7-2";
    }

    @Override
    protected String getDescription() {
        return "BA";
    }

    @Override
    protected String getPathInContainer() {
        return "/usr/share/burned_area";
    }

    @Override
    protected String getPathInSystem() {
        return null;
    }

    @Override
    protected String getContainerDescriptorFileName() {
        return "ba_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "ba_applications.json";
    }

    @Override
    protected String getLogoFileName() {
        return null;
    }

    @Override
    protected String[] additionalResources() {
        return new String[] { "ba.py", "model_gnb_ba.pkl" };
    }

}
