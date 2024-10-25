package ro.cs.tao.docker.snap;

import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.serialization.BaseSerializer;
import ro.cs.tao.serialization.MediaType;
import ro.cs.tao.serialization.SerializerFactory;
import ro.cs.tao.snap.xml.OperatorParser;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DescriptorBuilder {

    public static void main(String[] args) throws Exception {
        Path descriptorPath = Paths.get(DescriptorBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String[] tokens = "src/main/resources/ro/cs/tao/docker/snap".split("/");
        descriptorPath = descriptorPath.getParent().getParent();
        for (String token : tokens) {
            descriptorPath = descriptorPath.resolve(token);
        }
        descriptorPath = descriptorPath.resolve("snap11_container.json");
        Container container = new Container();
        container.setId(UUID.randomUUID().toString());
        container.setName("snap-10-0-0");
        container.setDescription("SNAP 10.0.0 Container");
        container.setTag("latest");
        container.setApplicationPath(null);
        container.setCommonParameters("-c 256");
        container.setFormatNameParameter("-f");
        container.addFormat("BEAM-DIMAP");
        container.addFormat("ENVI");
        container.addFormat("GeoTIFF");
        container.addFormat("GeoTIFF-BigTIFF");
        container.addFormat("GDAL-GTiff-WRITER");
        container.addFormat("JPEG2000");
        container.addFormat("HDF5");
        container.addFormat("NetCDF-BEAM");
        container.addFormat("NetCDF-CF");
        container.addFormat("ZNAP");
        final Map<String, String> map = listOperators();
        List<ProcessingComponent> components = new ArrayList<>();
        int i = 1;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            final Application application = new Application();
            application.setName(entry.getKey());
            application.setPath("gpt");
            application.setMemoryRequirements(12288);
            try {
                System.out.println("[" + i++ + "/" + map.size() + "] Extracting operator " + entry.getKey());
                final ProcessingComponent component = extractComponent(entry.getKey());
                component.setContainerId(container.getId());
                components.add(component);
                container.addApplication(application);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
        final BaseSerializer<Container> containerSerializer = SerializerFactory.create(Container.class, MediaType.JSON);
        containerSerializer.setIgnoreNullFields();
        Files.writeString(descriptorPath, containerSerializer.serialize(container));
        final BaseSerializer<ProcessingComponent> componentSerializer = SerializerFactory.create(ProcessingComponent.class, MediaType.JSON);
        Files.writeString(descriptorPath.getParent().resolve("snap11_operators.json"), componentSerializer.serialize(components, "components"));
        System.exit(0);
    }

    private static Map<String, String> listOperators() throws Exception {
        System.out.println("Extracting operator list");
        final Executor executor = Executor.create(ExecutorType.PROCESS,
                                                  "localhost", new ArrayList<>() {{ add("gpt"); }});
        final OutputAccumulator accumulator = new OutputAccumulator();
        accumulator.preserveLineSeparator(true);
        executor.setOutputConsumer(accumulator);
        executor.execute(true);
        String output = accumulator.getOutput();
        output = output.substring(output.indexOf("Operators:"));
        String[] lines = output.split("\n");
        lines = Arrays.copyOfRange(lines, 1, lines.length);
        final Map<String, String> list = new TreeMap<>();
        String currentName = null, currentDescription = null;
        for (String line : lines) {
            if (line.charAt(2) != ' ') {
                currentName = line.substring(2, 27).trim();
                currentDescription = line.substring(27).trim();
                list.put(currentName, currentDescription);
            } else {
                currentDescription += line.substring(27).trim();
                list.put(currentName, currentDescription);
            }
        }
        return list;
    }
    
    private static ProcessingComponent extractComponent(String operatorName) throws Exception {
        final Executor executor = Executor.create(ExecutorType.PROCESS,
                                                  "localhost", new ArrayList<>() {{ add("gpt"); add(operatorName); add("-h"); }});
        final OutputAccumulator accumulator = new OutputAccumulator();
        accumulator.preserveLineSeparator(true);
        executor.setOutputConsumer(accumulator);
        executor.execute(true);
        String output = accumulator.getOutput();
        output = output.substring(output.indexOf("Graph XML Format:"));
        String[] lines = output.split("\n");
        lines = Arrays.copyOfRange(lines, 1, lines.length);
        OperatorParser parser = new OperatorParser();
        return parser.parse(String.join("\n", lines));
    }

}