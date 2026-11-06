package ro.cs.tao.docker.psql;

import org.apache.commons.lang3.SystemUtils;
import ro.cs.tao.component.SystemVariable;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.docker.ContainerInstance;
import ro.cs.tao.persistence.PersistenceException;
import ro.cs.tao.topology.docker.DockerManager;
import ro.cs.tao.topology.docker.SingletonImageInstaller;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.Tuple;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * PostgreSQLContainer is a subclass of SingletonImageInstaller used to manage a
 * Docker container for running PostgreSQL 17 with PostGIS 3.5. It provides specific
 * configurations for the PostgreSQL container, including setting up paths, ports,
 * and initialization arguments.
 *
 * This class leverages the ConfigurationManager to retrieve configuration details
 * such as the data folder path and port values, and overrides methods from
 * SingletonImageInstaller to specify PostgreSQL-specific behavior.
 *
 * Fields include:
 * - pgDataFolder: The path to the PostgreSQL data directory on the host machine.
 * - pgPort: The port on which PostgreSQL will listen for connections.
 *
 * @author  Cosmin Cara
 * @since   1.6
 */
public class PostgreSQLContainer extends SingletonImageInstaller {
    private final String sharedPgDataFolder;
    private final int sharedPgPort;
    private static final String connectionStringTemplate = "jdbc:postgresql://localhost:%d/postgres?stringtype=unspecified";

    public PostgreSQLContainer() {
        super();
        ConfigurationProvider configurationProvider = ConfigurationManager.getInstance();
        this.sharedPgDataFolder = configurationProvider.getValue("psql.data.directory",
                                                                 Paths.get(SystemVariable.ROOT.value(), "pgData").toString());
        this.sharedPgPort = Integer.parseInt(configurationProvider.getValue("psql.port", "5555"));
    }

    @Override
    public String start(String user) throws IOException {
        if (!DockerHelper.isDockerFound()) {
            throw new IOException("This plugin requires Docker to be installed");
        }
        final int port = DockerManager.getNextFreePort();
        List<String> arguments = new ArrayList<>() {{
            add("--cpus");
            add("2");
            add("-e");
            add("POSTGRES_PASSWORD=tao");
        }};
        arguments.add("-v");
        final Path userPath = Paths.get(SystemVariable.ROOT.value()).resolve(user);
        final Path pgData = userPath.resolve("pgData");
        FileUtilities.ensureExists(pgData);
        arguments.add(FileUtilities.asUnixPath(pgData) + ":/var/lib/postgresql/data");
        arguments.add("-v");
        String uPath = FileUtilities.asUnixPath(userPath);
        arguments.add(uPath + ":" + uPath);
        final Tuple<Integer, Integer> portMap = new Tuple<>(port, 5432);
        DockerManager.removeContainer(user + "-postgis");
        final String containerId = DockerManager.runDaemon(getContainerName(), user + "-postgis", portMap, user, arguments);
        ContainerInstance instance = containerInstanceProvider().get(containerId);
        instance.setProperties(new HashMap<>() {{
            put("connectionString", String.format(connectionStringTemplate, port));
        }});
        try {
            containerInstanceProvider().update(instance);
        } catch (PersistenceException e) {
            logger.warning(e.getMessage());
        }
        return containerId;
    }

    @Override
    protected String getPathInSystem() {
        Path path = findInPath(SystemUtils.IS_OS_WINDOWS ?
                                       "psql.exe" : "psql");
        return path != null ? path.getParent().toString() : null;
    }

    @Override
    protected String getPathInContainer() { return "/usr/local/bin"; }

    @Override
    protected String getLogoFileName() {
        return "logo-psql.png";
    }

    @Override
    protected String getContainerDescriptorFileName() {
        return "psql_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "psql_applications.json";
    }

    @Override
    public String getContainerName() {
        return "postgis/postgis";
    }

    @Override
    public String getDescription() {
        return "PostgreSQL 17 with PostGIS 3.5";
    }
}
