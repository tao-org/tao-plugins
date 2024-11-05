package ro.cs.tao.tests.scripts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import ro.cs.tao.datasource.CollectionDescription;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.tests.scripts.internal.MockUSGSDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Adrian Draghici
 */
public class USGSParametersDescriptorsFetcher {

    private static final String PARAMETERS_DESCRIPTOR_FILE = "parameters.json";
    private static final String SENSORS_DESCRIPTOR_FILE = "sensors.json";
    private static final String SCRIPT_HELP = "Fetch and dump LIVE parameters/sensors descriptors script for USGS\n" +
            "Input/Parameters:\n" +
            "[-username | -u]: the USGS account username\n" +
            "[-token | -t]: the USGS account API token\n" +
            "[-output_directory | -od | -o]: the output directory path where the parameters descriptors will be generated\n" +
            "Output:\n" +
            "On the output directory 2 files will be generated:\n" +
            " - " + PARAMETERS_DESCRIPTOR_FILE + ": the parameters descriptors JSON file\n" +
            " - " + SENSORS_DESCRIPTOR_FILE + ": the sensors descriptors JSON file";

    private static final Logger LOGGER = Logger.getLogger(USGSParametersDescriptorsFetcher.class.getName());

    public static void main(String[] args) {
        LOGGER.info(SCRIPT_HELP);
        try {
            final InputParameters inputParameters = InputParameters.parseInputParameters(args);
            if (!Files.exists(inputParameters.outputDirectoryPath())) {
                Files.createDirectories(inputParameters.outputDirectoryPath());
            }
            LOGGER.info("\nInput parameters validated successfully.\nExecuting the script...\n");
            fetchAndDumpLiveParametersAndSensorsDescriptors(inputParameters);
            LOGGER.info("\nExecution finished.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Execution failed. Reason: " + e.getMessage(), e);
            e.printStackTrace();
        }
        System.exit(0);
    }

    private static void fetchAndDumpLiveParametersAndSensorsDescriptors(InputParameters inputParameters) throws Exception {
        final MockUSGSDataSource mockUSGSDataSource = new MockUSGSDataSource();
        mockUSGSDataSource.setCredentials(inputParameters.credentials().getUserPrincipal().getName(), inputParameters.credentials().getPassword());
        fetchAndDumpLiveParametersDescriptors(inputParameters, mockUSGSDataSource);
        fetchAndDumpLiveSensorsDescriptors(inputParameters, mockUSGSDataSource);
    }

    private static void fetchAndDumpLiveParametersDescriptors(InputParameters inputParameters, MockUSGSDataSource mockUSGSDataSource) throws Exception {
        final Map<String, Map<String, DataSourceParameter>> liveParametersDescriptors = mockUSGSDataSource.getSupportedParameters();
        new ObjectMapper().writeValue(Files.newOutputStream(inputParameters.outputDirectoryPath().resolve(PARAMETERS_DESCRIPTOR_FILE)), liveParametersDescriptors);
    }

    private static void fetchAndDumpLiveSensorsDescriptors(InputParameters inputParameters, MockUSGSDataSource mockUSGSDataSource) throws Exception {
        final Map<String, CollectionDescription> liveSensorsDescriptors = mockUSGSDataSource.getSensorTypes();
        new ObjectMapper().writeValue(Files.newOutputStream(inputParameters.outputDirectoryPath().resolve(SENSORS_DESCRIPTOR_FILE)), liveSensorsDescriptors);
    }

    private record InputParameters(Credentials credentials, Path outputDirectoryPath) {

            private static final String USERNAME_ARG_KEY = "-(username|u)";
            private static final String TOKEN_ARG_KEY = "-(token|t)";
            private static final String OUTPUT_DIR_ARG_KEY = "-(output_dir|od|o)";

            public static InputParameters parseInputParameters(String[] args) {
                if (args.length < 6) {
                    throw new IllegalArgumentException("missing parameters");
                }
                String username = null;
                String token = null;
                String outputDirectory = null;
                for (int i = 0; i < 6; i += 2) {
                    if (args[i].matches(USERNAME_ARG_KEY)) {
                        username = args[i + 1];
                        continue;
                    }
                    if (args[i].matches(TOKEN_ARG_KEY)) {
                        token = args[i + 1];
                        continue;
                    }
                    if (args[i].matches(OUTPUT_DIR_ARG_KEY)) {
                        outputDirectory = args[i + 1];
                    }
                }
                if (StringUtils.isEmpty(username) || StringUtils.isEmpty(token) || StringUtils.isEmpty(outputDirectory)) {
                    throw new IllegalArgumentException("one or more arguments is invalid");
                }
                final Credentials credentials = new UsernamePasswordCredentials(username, token);
                final Path outputDirectoryPath = Path.of(outputDirectory);
                return new InputParameters(credentials, outputDirectoryPath);
            }
    }

}
