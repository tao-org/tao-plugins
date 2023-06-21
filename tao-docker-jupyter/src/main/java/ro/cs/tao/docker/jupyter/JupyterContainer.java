package ro.cs.tao.docker.jupyter;

import ro.cs.tao.docker.Container;
import ro.cs.tao.topology.docker.BaseImageInstaller;
import ro.cs.tao.topology.docker.DockerManager;
import ro.cs.tao.topology.docker.SingletonContainer;
import ro.cs.tao.utils.DockerHelper;

import java.io.IOException;

public class JupyterContainer extends BaseImageInstaller implements SingletonContainer {

    public JupyterContainer() {
        super();
    }

    @Override
    public String getContainerName() {
        return "jupyterlite";
    }

    @Override
    protected String getDescription() {
        return "Jupyter Lite";
    }

    @Override
    protected String getPathInContainer() {
        return "/usr/bin";
    }

    @Override
    protected String getPathInSystem() {
        return null;
    }

    @Override
    protected String getContainerDescriptorFileName() {
        return "jupyter.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return null;
    }

    @Override
    protected String getLogoFileName() {
        return null;
    }

    @Override
    protected String[] additionalResources() {
        return new String[] {
                "pyKernel0requirements.txt"
        };
    }

    @Override
    public Container install() throws IOException {
        return super.installImage();
    }

    @Override
    public void start() throws IOException {
        if (!DockerHelper.isDockerFound()) {
            throw new IOException("This plugin requires Docker to be installed");
        }
        DockerManager.runDaemon(getContainerName(), null);
    }

    @Override
    public void shutdown() throws IOException {
        if (!DockerHelper.isDockerFound()) {
            throw new IOException("This plugin requires Docker to be installed");
        }
        DockerManager.stopContainer(getContainerName());
    }

    @Override
    public boolean isPerUser() {
        return true;
    }

    @Override
    public String getContainerDescription() {
        return "Container with a Jupyter Lite environment";
    }
}
