package ro.cs.tao.docker.otb;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ro.cs.tao.component.*;
import ro.cs.tao.component.enums.ComponentCategory;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.component.template.TemplateType;
import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.docker.ContainerType;
import ro.cs.tao.docker.ContainerVisibility;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.utils.executors.ProcessExecutor;

import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CliParser {
    private static Path otbPath;
    private static Path outDir;
    private static final Pattern paramPattern = Pattern.compile("-([a-z._0-9-]+)\\s+(\\<[a-z0-9]+\\>)\\s+([A-Za-z0-9 .-_,()]+)");
    private static final String defaultValueText = "default value is ";
    private static final Map<String, String> types = new HashMap<>();

    static {
        // uint8/uint16/int16/uint32/int32/float/double/cint16/cint32/cfloat/cdouble
        types.put("uint8", "byte");
        types.put("uint16", "short");
        types.put("int16", "short");
        types.put("uint32", "int");
        types.put("int32", "int");
        types.put("float", "float");
        types.put("double", "double");
    }

    public static void main(String[] args) throws IOException {
        if (args == null || args.length != 2) {
            System.exit(-1);
        }
        otbPath = Path.of(args[0]);
        outDir = Path.of(args[1]);
        final Path containerDescriptorFile = outDir.resolve("otb_container.json");
        final Path appsDescriptorFile = outDir.resolve("otb_applications.json");
        final List<Path> apps = listApplications(otbPath.resolve("bin"));
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.PUBLIC_ONLY);
        Container container = new Container();
        container.setId("otb-8-1-2");
        container.setType(ContainerType.DOCKER);
        container.setName("Orfeo Toolbox");
        container.setVisibility(ContainerVisibility.PUBLIC);
        container.setDescription("Orfeo Toolbox container");
        container.setApplicationPath("/opt/OTB-8.1.2-Linux64/bin");
        final StringBuilder appsJson = new StringBuilder();
        appsJson.append("[\n");
        for (Path app : apps) {
            try {
                System.out.println("Invoking " + app.getFileName());
                final String[] lines = getProcessOutput(app);
                Application application = new Application();
                application.setName(FileUtilities.getFilenameWithoutExtension(app).replace("otbcli_", ""));
                application.setPath(app.getFileName().toString());
                application.setMemoryRequirements(4096);
                container.addApplication(application);
                ProcessingComponent component = new ProcessingComponent();
                component.setId(application.getName());
                component.setContainerId(container.getId());
                component.setVisibility(ProcessingComponentVisibility.SYSTEM);
                component.setActive(true);
                component.setAuthors("CNES");
                component.setCopyright("(C)CNES");
                component.setComponentType(ProcessingComponentType.EXECUTABLE);
                component.setCategory(ComponentCategory.RASTER);
                component.setFileLocation(app.getFileName().toString().replace(".bat", ""));
                component.setMultiThread(true);
                component.setParallelism(4);
                component.setNodeAffinity(NodeAffinity.Any);
                component.setTemplateType(TemplateType.VELOCITY);
                component.setOwner(SystemPrincipal.instance().getName());
                parse(lines, component);
                final Set<ParameterDescriptor> params = component.getParameterDescriptors();
                final List<SourceDescriptor> sources = component.getSources();
                final List<TargetDescriptor> targets = component.getTargets();
                final StringBuilder builder = new StringBuilder();
                for (SourceDescriptor src : sources) {
                    builder.append("-").append(src.getName()).append(" ").append("$").append(src.getName()).append("\n");
                }
                for (ParameterDescriptor param : params) {
                    builder.append("-").append(param.getName()).append(" ").append("$").append(param.getName()).append("\n");
                }
                for (TargetDescriptor target : targets) {
                    builder.append("-").append(target.getName()).append(" ").append("$").append(target.getName()).append("\n");
                }
                component.setTemplateContents(builder.toString());
                String value = objectMapper.writerFor(ProcessingComponent.class).writeValueAsString(component);
                for (ParameterDescriptor param : params) {
                    value = value.replace(param.getDataType().getName(), param.javaType().friendlyName());
                }
                appsJson.append(value).append(",\n");
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
        appsJson.setLength(appsJson.length() - 1);
        appsJson.append("]\n");
        Files.writeString(containerDescriptorFile, objectMapper.writeValueAsString(container));
        Files.writeString(appsDescriptorFile, appsJson.toString());

        System.exit(0);
    }

    private static List<Path> listApplications(Path folder) throws IOException {
        return FileUtilities.listFiles(folder, "otbcli_");
    }

    private static String[] getProcessOutput(Path app) {
        final ProcessExecutor executor = (ProcessExecutor) ProcessExecutor.create(ExecutorType.PROCESS, "localhost",
                new ArrayList<String>() {{ add(app.toString()); }});
        final OutputAccumulator accumulator = new OutputAccumulator();
        accumulator.preserveLineSeparator(true);
        executor.setOutputConsumer(accumulator);
        try {
            executor.execute(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        final String output = accumulator.getOutput();
        return output != null ? output.split("\n") : new String[0];
    }

    private static void parse(String lines[], ProcessingComponent component) {
        int idx = 1;
        String line;
        component.setDescription("");
        while (!(line = lines[idx]).startsWith("Parameters")) {
            if (!line.trim().isEmpty()) {
                if (line.startsWith("This is the ")) {
                    component.setLabel(line.substring(12, line.indexOf(",")));
                    component.setVersion(line.substring(line.indexOf("version") + 8));
                } else {
                    component.setDescription(component.getDescription() + line + "\n");
                }
            }
            idx++;
        }
        while (!(line = lines[idx]).startsWith("Use ")) {
            final Matcher matcher = paramPattern.matcher(line);
            if (matcher.find()) {
                final String paramId = matcher.group(1);
                final String strType = matcher.group(2);
                final String descr = matcher.group(3);
                if ("in".equals(paramId)) {
                    SourceDescriptor src = new SourceDescriptor();
                    src.setId(UUID.randomUUID().toString());
                    src.setName(paramId);
                    src.setCardinality(strType.contains("list") ? 0 : 1);
                    DataDescriptor data = new DataDescriptor();
                    data.setFormatType(descr.contains("image") ? DataFormat.RASTER : descr.contains("vector") ? DataFormat.VECTOR : DataFormat.OTHER);
                    src.setDataDescriptor(data);
                    component.addSource(src);
                } else if ("out".equals(paramId)) {
                    TargetDescriptor trg = new TargetDescriptor();
                    trg.setId(UUID.randomUUID().toString());
                    trg.setName(paramId);
                    trg.setCardinality(strType.contains("list") ? 0 : 1);
                    DataDescriptor data = new DataDescriptor();
                    final String lowerCase = descr.toLowerCase();
                    data.setFormatType(lowerCase.contains("image") ? DataFormat.RASTER : lowerCase.contains("vector") ? DataFormat.VECTOR : DataFormat.OTHER);
                    data.setLocation("output_" + component.getId().toLowerCase() + (data.getFormatType() == DataFormat.RASTER ? ".tif" : data.getFormatType() == DataFormat.VECTOR ? ".shp" : ".csv"));
                    trg.setDataDescriptor(data);
                    component.addTarget(trg);
                } else if (!"help".equals(paramId) && !"<group>".equals(strType)) {
                    ParameterDescriptor param = new ParameterDescriptor();
                    param.setId(UUID.randomUUID().toString());
                    param.setName(paramId);
                    param.setLabel(param.getName());
                    Class<?> clazz;
                    if (strType.endsWith("list")) {
                        clazz = JavaType.fromFriendlyName(types.get(strType.substring(1, strType.indexOf(" ")))).value();
                        // For Java <= 11
                        param.setDataType(Array.newInstance(clazz, 0).getClass());
                        // For Java 12+ param.setDataType(clazz.arrayType());
                    } else {
                        clazz = JavaType.fromFriendlyName(types.get(strType.substring(1, strType.length() - 1))).value();
                        param.setDataType(clazz);
                    }
                    param.setDescription(descr);
                    if (descr.contains(defaultValueText)) {
                        param.setNotNull(false);
                        param.setDefaultValue(descr.substring(descr.indexOf(defaultValueText) + defaultValueText.length(), descr.length() - 1));
                    } else {
                        param.setNotNull(true);
                    }
                    component.addParameter(param);
                }
            }
            idx++;
        }
    }
}
