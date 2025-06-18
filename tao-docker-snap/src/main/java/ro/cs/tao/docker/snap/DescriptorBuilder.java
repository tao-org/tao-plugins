package ro.cs.tao.docker.snap;

import ro.cs.tao.component.ParameterDescriptor;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.enums.ParameterType;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.Visibility;
import ro.cs.tao.serialization.BaseSerializer;
import ro.cs.tao.serialization.MediaType;
import ro.cs.tao.serialization.SerializerFactory;
import ro.cs.tao.snap.xml.OperatorParser;
import ro.cs.tao.utils.async.Parallel;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DescriptorBuilder {

    static String containerId;

    public static void main(String[] args) throws Exception {
        Path descriptorPath = Paths.get(DescriptorBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String[] tokens = "src/main/resources/ro/cs/tao/docker/snap".split("/");
        descriptorPath = descriptorPath.getParent().getParent();
        for (String token : tokens) {
            descriptorPath = descriptorPath.resolve(token);
        }
        descriptorPath = descriptorPath.resolve("snap12_container.json");
        Container container = new Container();
        containerId = UUID.randomUUID().toString();
        container.setId(containerId);
        container.setName("snap-12-0-0");
        container.setDescription("SNAP 12.0.0 Container");
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
        final AtomicInteger i = new AtomicInteger(1);
        Parallel.ForEach(map.entrySet(), (entry) -> {
            final Application application = new Application();
            application.setName(entry.getKey());
            application.setPath("gpt");
            application.setMemoryRequirements(12288);
            try {
                System.out.println("[" + i.getAndIncrement() + "/" + map.size() + "] Extracting operator " + entry.getKey());
                final ProcessingComponent component = extractComponent(entry.getKey());
                component.setContainerId(container.getId());
                components.add(component);
                container.addApplication(application);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        });
        final BaseSerializer<Container> containerSerializer = SerializerFactory.create(Container.class, MediaType.JSON);
        containerSerializer.setIgnoreNullFields();
        Files.writeString(descriptorPath, containerSerializer.serialize(container));
        final BaseSerializer<ProcessingComponent> componentSerializer = SerializerFactory.create(ProcessingComponent.class, MediaType.JSON);
        componentSerializer.setIgnoreNullFields();
        componentSerializer.setFormatOutput(true);
        String serialized = componentSerializer.serialize(components, "components");
        serialized = serialized.replaceAll("\"formatType\" : 0", "\"formatType\" : \"" + DataFormat.RASTER.name() + "\"");
        serialized = serialized.replaceAll("\"visibility\" : 0", "\"visibility\" : \"" + ProcessingComponentVisibility.SYSTEM.name() + "\"");
        serialized = serialized.replaceAll("\"componentType\" : 0", "\"componentType\" : \"" + ProcessingComponentType.EXECUTABLE.name() + "\"");
        serialized = serialized.replaceAll("\"type\" : 0", "\"type\" : \"" + ParameterType.REGULAR.name() + "\"");
        serialized = serialized.replaceAll(JavaType.BOOLEAN.value().getName(), JavaType.BOOLEAN.friendlyName());
        serialized = serialized.replaceAll(JavaType.STRING.value().getName(), JavaType.STRING.friendlyName());
        serialized = serialized.replaceAll(JavaType.DOUBLE.value().getName(), JavaType.DOUBLE.friendlyName());
        serialized = serialized.replaceAll(JavaType.INT.value().getName(), JavaType.INT.friendlyName());
        serialized = serialized.replaceAll(JavaType.BOOLEAN.value().getName(), JavaType.BOOLEAN.friendlyName());
        serialized = serialized.replaceAll(JavaType.BOOLEAN.value().getName(), JavaType.BOOLEAN.friendlyName());
        Files.writeString(descriptorPath.getParent().resolve("snap12_operators.json"), serialized);
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
        final int graphIdx = output.indexOf("Graph XML Format:");
        String graph = output.substring(graphIdx);
        String[] lines = graph.split("\n");
        lines = Arrays.copyOfRange(lines, 1, lines.length);
        OperatorParser parser = new OperatorParser(containerId);
        final ProcessingComponent component = parser.parse(String.join("\n", lines));

        final int paramIdx = output.indexOf("Parameter Options:");
        // there are some components (e.g. Tool Adapter) without this section for Parameter Options
        if (paramIdx >= 0 && graphIdx <= output.length() && paramIdx < graphIdx) {
            output = output.substring(paramIdx, graphIdx);
            lines = output.split("\n");
            final Map<String, ParameterDescriptor> map = component.getParameterDescriptors().stream().collect(Collectors.toMap(ParameterDescriptor::getLabel, Function.identity()));
            String description = "";
            String label;
            String type;
            ParameterDescriptor descriptor = null;
            for (String line : lines) {
                if (!(line.startsWith("Parameter Options:"))) {
                    int idx = line.indexOf('=');
                    if (idx > 0) {
                        label = line.substring(line.indexOf('-') + 1, idx);
                        description = line.substring(line.indexOf('>') + 1).trim();
                        descriptor = map.get(label);
                        if (descriptor == null) {
                            continue;
                        }
                        descriptor.setDescription(description);
                        idx = description.indexOf("must be one of ");
                        if (idx > 0) {
                            final String[] split = description.substring(idx + 15, description.indexOf('.', idx)).split(",");
                            descriptor.setValueSet(Arrays.stream(split).map(new Function<String,String>() {
                                @Override
                                public String apply(String s) {
                                    return s.replace("'", "");
                                }
                            }).toArray(String[]::new));
                        }
                    } else {
                        description += line.trim();
                        descriptor.setDescription(description);
                        idx = description.indexOf("must be one of ");
                        if (idx > 0) {
                            final String[] split = description.substring(idx + 15, description.indexOf('.', idx)).split(",");
                            descriptor.setValueSet(Arrays.stream(split).map(new Function<String,String>() {
                                @Override
                                public String apply(String s) {
                                    return s.replace("'", "").trim();
                                }
                            }).toArray(String[]::new));
                        }
                    }
                    final int first = line.indexOf("Default value is");
                    if (first > 0) {
                        descriptor.setDefaultValue(line.substring(first + 18, line.indexOf("'", first + 18)));
                    }
                }
            }
        } else{
            System.out.println("*** Component " + component.getId() + " has no Parameter Options");
        }
        return component;
    }
}