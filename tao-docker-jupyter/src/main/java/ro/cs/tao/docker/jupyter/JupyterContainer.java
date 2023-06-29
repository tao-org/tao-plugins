package ro.cs.tao.docker.jupyter;

import ro.cs.tao.docker.Container;
import ro.cs.tao.docker.DockerVolumeMap;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.topology.docker.BaseImageInstaller;
import ro.cs.tao.topology.docker.DockerManager;
import ro.cs.tao.topology.docker.SingletonContainer;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.Tuple;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
        return "/home/jovyan/notebooks";
    }

    @Override
    protected String getPathInSystem() {
        return null;
    }

    @Override
    protected String getContainerDescriptorFileName() {
        return "jupyter_container.json";
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
    public String start(String userName, String token) throws IOException {
        if (!DockerHelper.isDockerFound()) {
            throw new IOException("This plugin requires Docker to be installed");
        }
        final String containerName = getContainerName();
        final String id = DockerManager.getInstance(containerName, userName);
        if (id != null) {
            throw new RuntimeException(String.format("[%s] is already running", containerName));
        }
        final Properties properties = getAdditionalProperties();
        final List<String> args = new ArrayList<>();
        args.add("--init");
        Tuple<Integer, Integer> portMap = null;
        if (properties.size() > 0) {
            if (properties.containsKey("host.port") && properties.containsKey("container.port")) {
                final String containerPort = properties.getProperty("container.port");
                if (!StringUtilities.isNullOrEmpty(containerPort)) {
                    final int hostPort = DockerManager.getNextFreePort();
                    portMap = new Tuple<>(hostPort, Integer.parseInt(containerPort));
                }
            }
            if (properties.containsKey("env.JUPYTER_TOKEN")) {
                final String env = properties.getProperty("env.JUPYTER_TOKEN");
                String authToken = StringUtilities.isNullOrEmpty(env) ? token : env;
                args.add("-e");
                args.add("JUPYTER_TOKEN=" + authToken);
            }
        }
        args.add("-v");
        final DockerVolumeMap map = ExecutionConfiguration.getMasterContainerVolumeMap();
        args.add((map.getHostWorkspaceFolder().endsWith("/")
                  ? map.getHostWorkspaceFolder()
                  : (map.getHostWorkspaceFolder() + "/"))
                         + userName + ":" + getPathInContainer());
        return DockerManager.runDaemon(containerName, userName, portMap, userName, args);
    }

    @Override
    public void shutdown() throws IOException {
        if (!DockerHelper.isDockerFound()) {
            throw new IOException("This plugin requires Docker to be installed");
        }
        DockerManager.stopInstances(getContainerName());
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
