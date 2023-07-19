package ro.cs.tao.docker.jupyter;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.docker.Container;
import ro.cs.tao.docker.DockerVolumeMap;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.topology.TopologyManager;
import ro.cs.tao.topology.docker.BaseImageInstaller;
import ro.cs.tao.topology.docker.DockerManager;
import ro.cs.tao.topology.docker.SingletonContainer;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.Tuple;
import ro.cs.tao.utils.executors.*;

import java.io.IOException;
import java.net.InetAddress;
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
        return new String[]{
                "pyKernel0requirements.txt", "jupyter_notebook_config.py", "jupyterlite.key", "jupyterlite.pem",
                "custom.js"
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
        final DockerVolumeMap map = ExecutionConfiguration.getMasterContainerVolumeMap();
        final List<String> args = new ArrayList<>();
        args.add("--init");
        args.add("-e");
        args.add("JUPYTER_HOST_NAME=" + TopologyManager.getInstance().getMasterNodeInfo().getId());
        // handle permission denied on linux machines
        if (SystemUtils.IS_OS_LINUX) {
            List<String> arg = new ArrayList<>();
            arg.add("id");
            arg.add("-u");
            arg.add(System.getProperty("user.name"));
            ExecutionUnit uIdJob = new ExecutionUnit(ExecutorType.PROCESS,
                    InetAddress.getLocalHost().getHostName(),
                    null, null,
                    arg,
                    ExecutionMode.USER.value(),
                    null);
            OutputAccumulator uIdAccumulator = new OutputAccumulator();
            Executor.execute(uIdAccumulator, 10, uIdJob);
            arg.clear();
            // recover current user id
            int uId = Integer.parseInt(uIdAccumulator.getOutput());
            args.add("NB_UID=" + uId);
            arg.add("setfacl");
            arg.add("-R");
            arg.add("-m");
            arg.add("u:" + uId + ":rwx");
            arg.add(map.getHostWorkspaceFolder());

            ExecutionUnit setfaclJob = new ExecutionUnit(ExecutorType.PROCESS,
                    InetAddress.getLocalHost().getHostName(),
                    null, null,
                    arg,
                    ExecutionMode.USER.value(),
                    null);
            OutputAccumulator setfaclAccumulator = new OutputAccumulator();
            Executor.execute(setfaclAccumulator, 10, setfaclJob);
        }
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
