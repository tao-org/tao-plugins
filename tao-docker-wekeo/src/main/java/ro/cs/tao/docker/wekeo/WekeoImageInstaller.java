package ro.cs.tao.docker.wekeo;

import ro.cs.tao.topology.docker.BaseImageInstaller;


public class WekeoImageInstaller extends BaseImageInstaller{
	
	@Override
    public String getContainerName() {
        return "wekeo-1-0-0";
    }

	@Override
    protected String getDescription() {
        return "WEKEO";
    }

	@Override
    protected String getPathInContainer() {
        return "/app";
    }

	@Override
    protected String getPathInSystem() {
        return null;
    }

	@Override
    protected String getContainerDescriptorFileName() {
        return "wekeo_container.json";
    }

	@Override
    protected String getComponentDescriptorFileName() {
        return "wekeo_applications.json";
    }

	@Override
    protected String getLogoFileName() {
        return "wekeo_logo.png";
    }

	@Override
    protected String[] additionalResources() {
        return new String[] {
                "fetchApiWekEO.py", "requirements.txt"
        };
    }
}