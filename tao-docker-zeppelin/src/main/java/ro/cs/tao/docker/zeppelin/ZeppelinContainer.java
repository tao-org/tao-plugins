package ro.cs.tao.docker.zeppelin;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.component.SystemVariable;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.docker.Container;
import ro.cs.tao.docker.ContainerType;
import ro.cs.tao.topology.docker.BaseImageInstaller;
import ro.cs.tao.topology.docker.DockerManager;
import ro.cs.tao.topology.docker.SingletonContainer;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.Tuple;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ZeppelinContainer extends BaseImageInstaller implements SingletonContainer {

    public ZeppelinContainer(){ super(); }
    @Override
    public String getContainerName() {
        return "apachezeppelin";
    }

    @Override
    protected String getDescription() {
        return "Apache Zeppelin";
    }

    @Override
    protected String getPathInContainer() {
        return "/opt/zeppelin/notebook";
    }

    @Override
    protected String getPathInSystem() {
        return null;
    }

    @Override
    protected String getContainerDescriptorFileName() {
        return "zeppelin_container.json";
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
    public String getContainerDescription() {
        return "Container with an Apache Zeppelin environment";
    }

    @Override
    protected String[] additionalResources() {
        return new String[]{
                "requirements.txt", "zeppelin-site.xml"
        };
    }


    @Override
    public Container install() throws IOException {
        Container container = null;
        if (dockerPath == null) {
            logger.warning("[docker] was not found in system path, cancelling installation");
            return null;
        }
        try{
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
        Container dbContainer = this.containerProvider.getByName(getContainerName());
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
    public String start(String user, String token) throws IOException {
        if (!DockerHelper.isDockerFound()) {
            throw new IOException("This plugin requires Docker to be installed");
        }
        final String containerName = getContainerName();
        final String id = DockerManager.getInstance(containerName, user);
        if (id != null) {
            throw new RuntimeException(String.format("[%s] is already running", containerName));
        }
        final List<String> args = new ArrayList<>();
        // add environment variables and connection
        args.add("--init");
        args.add("-e");
        args.add("ZEPPELIN_ADDR=0.0.0.0");
        // authentication

        //port env
        final int hostPort = DockerManager.getNextFreePort();
        args.add("-e");
        args.add("ZEPPELIN_PORT=" + 8080);
        Tuple<Integer, Integer> portMap = new Tuple<>(hostPort, 8080);
        args.add("-v");
        Path absolutePath = Paths.get(SystemVariable.ROOT.value()).toAbsolutePath();
        String mapVolumePath = (absolutePath.endsWith("/") || absolutePath.endsWith("\\")
                ? absolutePath
                : SystemUtils.IS_OS_LINUX ? (absolutePath + "/") + user
                : (absolutePath + "\\")) + user;
        File file = new File(mapVolumePath);
        if(!file.exists())
            file.mkdir();
        args.add(mapVolumePath + ":" + getPathInContainer());
        String instanceId = DockerManager.runDaemon(containerName, "apachezeppelin" + token.substring(0,5), portMap, user, args);
        if(instanceId ==  null)
            throw new RuntimeException("Error creating Apache Zeppelin Contianer");
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
}
