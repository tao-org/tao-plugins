package ro.cs.tao.docker.jupyter;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.component.SystemVariable;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.docker.Container;
import ro.cs.tao.docker.ContainerType;
import ro.cs.tao.docker.DockerVolumeMap;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.topology.docker.BaseImageInstaller;
import ro.cs.tao.topology.docker.DockerManager;
import ro.cs.tao.topology.docker.SingletonContainer;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.Tuple;
import ro.cs.tao.utils.executors.*;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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
        Container container = null;
        if (dockerPath != null) {
            try {
                final String localRegistry = ConfigurationManager.getInstance().getValue("tao.docker.registry");
                if ((container = DockerManager.getDockerImage(localRegistry + "/" + getContainerName())) == null &&
                        (container = DockerManager.getDockerImage(getContainerName())) == null) {
                    // first try the container.properties file, maybe it is a container from Docker Hub
                    Path dockerfilePath = dockerImagesPath.resolve(getContainerName()).resolve("container.properties");
                    FileUtilities.createDirectories(dockerImagesPath.resolve(getContainerName()));
                    //dockerfilePath = dockerfilePath.resolve("container.properties");
                    final byte[] buffer = new byte[1024];
                    try (InputStream is = getClass().getResourceAsStream("container.properties");
                         OutputStream os = new BufferedOutputStream(Files.newOutputStream(dockerfilePath))) {
                        if (is != null) {
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                os.write(buffer, 0, read);
                            }
                            os.flush();
                        }
                    } catch (Exception notFound) {
                        logger.fine(String.format("Container %s doesn't have container.properties", getContainerName()));
                    }
                    if (Files.exists(dockerfilePath)) {
                        additionalProperties.load(Files.newBufferedReader(dockerfilePath));
                    }
                    final String dockerHubName = additionalProperties.getProperty("docker.hub.name");
                    if (!additionalProperties.isEmpty() && !StringUtilities.isNullOrEmpty(dockerHubName)) {
                        container = DockerManager.pullImage(dockerHubName);
                    } else {
                        dockerfilePath = dockerImagesPath.resolve(getContainerName()).resolve("Dockerfile");
                        if (!Files.exists(dockerfilePath) || Files.size(dockerfilePath) == 0) {
                            Files.deleteIfExists(dockerfilePath);
                            logger.finest(String.format("Extracting Dockerfile for image %s", getContainerName()));
                            Files.write(dockerfilePath, readResource("Dockerfile"));
                        }
                        container = DockerManager.getDockerImage(getContainerName());
                    }
                    if (container == null) {
                        this.logger.info(String.format("Image %s was not found in Docker registry. Registration starting.\n" +
                                "Until registration completes, the corresponding components will not be available.", getContainerName()));
                        for (String resource : additionalResources()) {
                            //Files.write(dockerfilePath.getParent().resolve(Paths.get(resource).getFileName()), readResource(resource));
                            Files.write(dockerfilePath.getParent().resolve(Paths.get(resource)),
                                    readResource(resource));
                        }
                        DockerManager.registerImage(dockerfilePath.toRealPath(), getContainerName(), getDescription());
                        this.logger.info(String.format("Registration completed for docker image %s.", getContainerName()));
                        container = DockerManager.getDockerImage(getContainerName());
                    } else {
                        logger.finest(String.format("Image %s was found in Docker registry", getContainerName()));
                    }
                }
            } catch (IOException e) {
                logger.warning(String.format("Error occurred while registering %s: %s",
                        getContainerName(), e.getMessage()));
            }
        }
        Container dbContainer = this.containerProvider.get(container != null ? container.getId() : getContainerName());
        if (dbContainer == null) {
            logger.info(String.format("Container %s not registered in database, will create one", getContainerName()));
            dbContainer = new Container();
            dbContainer.setId(container != null ? container.getId() : getContainerName());
            dbContainer.setName(getContainerName());
            dbContainer.setTag(container != null ? container.getTag() : getContainerName());
            dbContainer.setType(ContainerType.DOCKER);
            dbContainer = initializeContainer(dbContainer, dockerPath != null ? getPathInContainer() : getPathInSystem());
            if (dbContainer == null) {
                logger.severe(String.format("Container %s failed to register", getContainerName()));
            } else {
                logger.fine(String.format("Container %s registered with id '%s'", getContainerName(), dbContainer.getId()));
            }
        }
        return dbContainer;
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
        //final Properties properties = getAdditionalProperties();
        final DockerVolumeMap map = ExecutionConfiguration.getMasterContainerVolumeMap();
        final List<String> args = new ArrayList<>();
        args.add("--init");
        args.add("-e");
        final String siteUrl = ConfigurationManager.getInstance().getValue("tao.services.base");
        int columnIdx = siteUrl.indexOf(":", siteUrl.indexOf("//") + 2);
        args.add("JUPYTER_HOST_NAME=" + (columnIdx > 0
                ? siteUrl.substring(siteUrl.indexOf("//") + 2, columnIdx)
                : siteUrl.substring(siteUrl.indexOf("//") + 2)));
        args.add("-e");
        args.add("JUPYTER_HOST_URL=" + siteUrl);
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
            args.add("-e");
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

        final int hostPort = DockerManager.getNextFreePort();
        args.add("-e");
        args.add("JUPYTER_PORT=" + hostPort);
        Tuple<Integer, Integer> portMap = new Tuple<>(hostPort, hostPort);
        args.add("-e");
        args.add("JUPYTER_TOKEN=" + token);
        args.add("-v");
        Path absolutePath = Paths.get(SystemVariable.ROOT.value()).toAbsolutePath();
        String mapVolumePath = (absolutePath.endsWith("/")
                ? absolutePath
                : (absolutePath + "/"))
                + userName;
        File file = new File(mapVolumePath);
        if(!file.exists())
        {
            file.mkdir();
        }
        args.add(mapVolumePath + ":" + getPathInContainer());
        String instanceId = DockerManager.runDaemon(containerName, userName, portMap, userName, args);
        return instanceId + ":" + hostPort;
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
