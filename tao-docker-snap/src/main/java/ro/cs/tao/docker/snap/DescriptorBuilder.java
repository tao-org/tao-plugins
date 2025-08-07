package ro.cs.tao.docker.snap;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.reflections.Reflections;
import ro.cs.tao.EnumUtils;
import ro.cs.tao.TaoEnum;
import ro.cs.tao.component.ParameterDescriptor;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.component.TargetDescriptor;
import ro.cs.tao.component.enums.ComponentCategory;
import ro.cs.tao.component.enums.ParameterType;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.component.template.TemplateType;
import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.serialization.BaseSerializer;
import ro.cs.tao.serialization.MediaType;
import ro.cs.tao.serialization.SerializerFactory;
import ro.cs.tao.snap.xml.OperatorParser;
import ro.cs.tao.utils.async.Parallel;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;

import java.io.IOException;
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
        if (args == null || args.length == 0) {
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
        } else if (args.length == 2) {
            final BaseSerializer<ProcessingComponent> componentSerializer = SerializerFactory.create(ProcessingComponent.class, MediaType.JSON);
            componentSerializer.setIgnoreNullFields();
            componentSerializer.setFormatOutput(true);
            Reflections reflections = new Reflections("ro.cs.tao");
            Set<Class<? extends TaoEnum>> enums = reflections.getSubTypesOf(TaoEnum.class);
            for (Class anEnum : enums) {
                componentSerializer.addDeserializer(anEnum, new StdDeserializer(anEnum) {

                    @Override
                    public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
                        if (anEnum.equals(JavaType.class)) {
                            return JavaType.fromFriendlyName(jsonParser.getText());
                        } else {
                            try {
                                return EnumUtils.getEnumConstantByName(anEnum, jsonParser.getText());
                            } catch (IllegalArgumentException e) {
                                return EnumUtils.getEnumConstantByValue(anEnum, jsonParser.getText());
                            }
                        }
                    }
                });
                componentSerializer.addSerializer(new StdSerializer(anEnum) {

                    @Override
                    public void serialize(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                        if (o instanceof JavaType) {
                            jsonGenerator.writeString(((JavaType) o).friendlyName());
                        } else {
                            Enum enumConstant = (Enum) o;
                            jsonGenerator.writeString(enumConstant.name());
                        }
                    }
                });
            }
            List<ProcessingComponent> sources = componentSerializer.deserialize(ProcessingComponent.class, Files.readString(Paths.get(args[0])));
            List<ProcessingComponent> targets = componentSerializer.deserialize(ProcessingComponent.class, Files.readString(Paths.get(args[1])));
            mergeDescriptors(sources, targets);
            for (ProcessingComponent component : targets) {
                component.setTemplateType(TemplateType.VELOCITY);
                if (component.getCategory() == null) {
                    component.setCategory(ComponentCategory.MISC);
                }
                for (ParameterDescriptor descriptor : component.getParameterDescriptors()) {
                    if (descriptor.getName() == null) {
                        descriptor.setName(descriptor.getId());
                    }
                }
            }
            String serialized = componentSerializer.serialize(targets, "components");
            List<JavaType> arrayTypes = Arrays.stream(JavaType.values()).filter(t -> t.isArrayType()).collect(Collectors.toList());
            List<JavaType> simpleTypes = Arrays.stream(JavaType.values()).filter(t -> !t.isArrayType()).collect(Collectors.toList());
            for (JavaType javaType : arrayTypes) {
                String value = "\\" + javaType.value().getName();
                serialized = serialized.replaceAll(value, javaType.friendlyName());
            }
            for (JavaType javaType : simpleTypes) {
                serialized = serialized.replaceAll(javaType.value().getName(), javaType.friendlyName());
            }
            Files.writeString(Paths.get(args[1]), serialized);
        }
        System.exit(0);
    }

    private static void mergeDescriptors(List<ProcessingComponent> sources, List<ProcessingComponent> targets) {
        Map<String, ProcessingComponent> sourceComponents = sources.stream().collect(Collectors.toMap(ProcessingComponent::getId, Function.identity()));
        Map<String, ProcessingComponent> targetComponents = targets.stream().collect(Collectors.toMap(ProcessingComponent::getId, Function.identity()));
        for (Map.Entry<String, ProcessingComponent> entry : sourceComponents.entrySet()) {
            ProcessingComponent source = entry.getValue();
            ProcessingComponent target = targetComponents.get(entry.getKey());
            if (target != null) {
                System.out.println(target.getId());
                target.setCategory(source.getCategory());
                target.setDescription(source.getDescription());
                target.setTemplateType(TemplateType.VELOCITY);
                target.setTemplateContents(source.getTemplateContents().substring(0, source.getTemplateContents().indexOf("\n") + 1) + target.getTemplateContents());
                for (SourceDescriptor descriptor : target.getSources()) {
                    descriptor.setParentId(target.getId());
                    SourceDescriptor sourceDescriptor = source.getSources().stream()
                                                              .filter(s -> s.getId() != null &&  (s.equals(descriptor.getId()) || s.getName().equals(descriptor.getName())))
                                                              .findFirst().orElse(null);
                    if (sourceDescriptor != null) {
                        descriptor.setId(sourceDescriptor.getId());
                    }
                }
                for (TargetDescriptor descriptor : target.getTargets()) {
                    descriptor.setParentId(target.getId());
                    TargetDescriptor sourceDescriptor = source.getTargets().stream()
                                                              .filter(t -> t.getId() != null && (t.getId().equals(descriptor.getId()) || t.getName().equals(descriptor.getName())))
                                                              .findFirst().orElse(null);
                    if (sourceDescriptor != null) {
                        descriptor.setId(sourceDescriptor.getId());
                    }
                }
            } else {
                System.err.println(source.getId() + " not found in target descriptors");
                for (SourceDescriptor descriptor : source.getSources()) {
                    descriptor.setParentId(source.getId());
                }
                for (TargetDescriptor descriptor : source.getTargets()) {
                    descriptor.setParentId(source.getId());
                }
                targets.add(source);

            }
        }

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