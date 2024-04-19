package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.docker.DockerVolumeMap;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.execution.JobCompletedListener;
import ro.cs.tao.execution.model.ExecutionJob;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.executors.container.ContainerUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BashExecutor extends Executor<Object>
                            implements ExecutionDescriptorConverter, JobCompletedListener {
    private static final String skeleton = "Help()\n" +
            "{\n" +
            "   # Display Help\n" +
            "   echo \"Syntax: %s [-h] %s\"\n" +
            "   echo \"options:\"\n" +
            "   echo \"h     Print this Help.\"\n" +
            "   echo\n" +
            "}\n";
    private ExecutionUnit unit;

    public BashExecutor() {
    }

    public BashExecutor(ExecutionUnit unit) {
        super(unit.getHost(), unit.getArguments(), unit.asSuperUser());
        this.unit = unit;
    }

    public void setUnit(ExecutionUnit unit) {
        this.unit = unit;
    }

    @Override
    public boolean isIntendedFor(ExecutionUnitFormat unitFormat) {
        return ExecutionUnitFormat.BASH == unitFormat;
    }

    @Override
    public int execute(boolean logMessages) throws Exception {
        if (this.unit == null) {
            throw new NullPointerException("Execution unit not set");
        }
        if (unit.getScriptTargetPath() != null) {
            try {
                String cmdLine;
                final Path path = Paths.get(unit.getScriptTargetPath());
                if (!Files.exists(path)) {
                    cmdLine = asCommandLine(this.unit, true);
                    Files.write(path, ("#!/bin/bash\n" + cmdLine).getBytes(), StandardOpenOption.CREATE);
                } else {
                    cmdLine = asCommandLine(this.unit, false);
                    Files.write(path, cmdLine.getBytes(), StandardOpenOption.APPEND);
                }
                this.outputConsumer.consume(cmdLine);
            } catch (IOException e) {
                logger.severe(ExceptionUtils.getStackTrace(logger, e));
                return 1;
            }
        }
        return 0;
    }

    @Override
    public boolean canConnect() {
        return true;
    }

    @Override
    public void onCompleted(ExecutionJob job) {
        final Path outputsFile = Paths.get(unit.getScriptTargetPath()).getParent().resolve("outputs.txt");
        if (Files.exists(outputsFile)) {
            final Map<String, Integer> outSet = new LinkedHashMap<>();
            try {
                outSet.putAll((Map<String, Integer>) new ObjectMapper().readValue(Files.readAllBytes(outputsFile), LinkedHashMap.class));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }

    private String asCommandLine(ExecutionUnit unit, boolean isFirst) {
        final StringBuilder builder = new StringBuilder();
        final ContainerUnit container = unit.getContainerUnit();
        final Map<String, Object> metadata = unit.getMetadata();
        Map<String, String> volumeMap = container.getVolumeMap();
        List<String> args = unit.getArguments();
        final DockerVolumeMap map = ExecutionConfiguration.getMasterContainerVolumeMap();
        String comment = "# ";
        if (metadata != null ) {
            if (metadata.containsKey("name")) {
                comment += metadata.get("name") + " ";
            }
            if (metadata.containsKey("id")) {
                comment += "[" + metadata.get("id") + "] ";
            }
            if (metadata.containsKey("dependsOn")) {
                comment += "(depends on: ";
                final List<Long> list = (List<Long>) metadata.get("dependsOn");
                comment += list.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
            }
            Map<String, Integer> inputSet = new LinkedHashMap<>();
            final Path inputsFile = Paths.get(unit.getScriptTargetPath()).getParent().resolve("inputs.txt");
            if (isFirst) {
                String inputList = (String) metadata.get("inputs");
                String[] inputs;
                if (inputList == null) {
                    Map<String, String> outputs = (Map<String, String>) metadata.get("taskOutput");
                    inputs = outputs.values().toArray(new String[0]);
                } else {
                    inputs = inputList.split(",");
                }
                String hostWorkspaceFolder = map.getHostWorkspaceFolder();
                if (hostWorkspaceFolder.endsWith("/")) {
                    hostWorkspaceFolder = hostWorkspaceFolder.substring(0, hostWorkspaceFolder.length() - 1);
                }
                String containerWorkspaceFolder = map.getContainerWorkspaceFolder();
                if (containerWorkspaceFolder.endsWith("/")) {
                    containerWorkspaceFolder = containerWorkspaceFolder.substring(0, containerWorkspaceFolder.length() - 1);
                }
                for (int i = 0; i < inputs.length; i++) {
                    inputSet.put(inputs[i].replace("file:///", "/").replace(":", "")
                                          .replace(hostWorkspaceFolder, containerWorkspaceFolder)
                                          .replace(map.getHostEODataFolder(), map.getContainerEoDataFolder())
                                          .replace(map.getHostConfigurationFolder(), map.getContainerConfigurationFolder()),
                                 i + 1);
                }
                try {
                    Files.write(inputsFile, new ObjectMapper().writeValueAsBytes(inputSet));
                } catch (Exception e) {
                    logger.severe(ExceptionUtils.getStackTrace(logger, e));
                }
            } else {
                if (Files.exists(inputsFile)) {
                    try {
                        inputSet.putAll((Map<String, Integer>) new ObjectMapper().readValue(Files.readAllBytes(inputsFile), LinkedHashMap.class));
                    } catch (IOException e) {
                        logger.severe(ExceptionUtils.getStackTrace(logger, e));
                    }
                }
                final Map<String, Integer> outSet = new LinkedHashMap<>();
                int idx = inputSet.size() + 1;
                if (metadata.containsKey("isTerminal") && (Boolean) metadata.get("isTerminal")) {
                    final Path outputsFile = Paths.get(unit.getScriptTargetPath()).getParent().resolve("outputs.txt");
                    final Map<String, String> outList = (Map<String, String>) metadata.get("taskOutput");
                    String hostWorkspaceFolder = map.getHostWorkspaceFolder();
                    if (hostWorkspaceFolder.endsWith("/")) {
                        hostWorkspaceFolder = hostWorkspaceFolder.substring(0, hostWorkspaceFolder.length() - 1);
                    }
                    String containerWorkspaceFolder = map.getContainerWorkspaceFolder();
                    if (containerWorkspaceFolder.endsWith("/")) {
                        containerWorkspaceFolder = containerWorkspaceFolder.substring(0, containerWorkspaceFolder.length() - 1);
                    }
                    if (outList != null && !outList.isEmpty()) {
                        try {
                            if (Files.exists(outputsFile)) {
                                outSet.putAll((Map<String, Integer>) new ObjectMapper().readValue(Files.readAllBytes(outputsFile), LinkedHashMap.class));
                                idx += outSet.size();
                            } else {
                                Files.createFile(outputsFile);
                            }
                            for (Map.Entry<String, String> entry : outList.entrySet()) {
                                outSet.put(entry.getValue().replace("file:///", "/").replace(":", "")
                                                 .replace(hostWorkspaceFolder, containerWorkspaceFolder)
                                                 .replace(map.getHostEODataFolder(), map.getContainerEoDataFolder())
                                                 .replace(map.getHostConfigurationFolder(), map.getContainerConfigurationFolder()),
                                           idx);
                            }
                            Files.write(outputsFile, new ObjectMapper().writeValueAsBytes(outSet), StandardOpenOption.TRUNCATE_EXISTING);
                        } catch (IOException e) {
                            logger.severe(ExceptionUtils.getStackTrace(logger, e));
                        }
                    }
                }
            }
            List<String> arguments;
            final boolean isBash = args.get(0).equals("/bin/bash");
            if (isBash) {
                arguments = Arrays.asList(args.get(2).split(" "));
            } else {
                arguments = args;
            }
            String positionalArgument, variable;
            for (int i = 0; i < arguments.size(); i++) {
                String arg = arguments.get(i);
                String found = null;
                int index = 0;
                for (Map.Entry<String, Integer> entry : inputSet.entrySet()) {
                    if (arg.contains(entry.getKey())) {
                        found = entry.getKey();
                        index = entry.getValue();
                        break;
                    }
                }
                if (found != null) {
                    positionalArgument = "$" + index;
                    variable = "argument" + index;
                    for (int j = 1; j < arguments.size(); j++) {
                        if (arguments.get(j).equals(arg)) {
                            arguments.set(j, "$" + (isBash ? "{" + variable + "}" : variable));
                        }
                    }
                    //appendLine(builder, variable + "=" + positionalArgument);
                    //appendLine(builder, variable + "=${" + variable + "/" + map.getHostWorkspaceFolder() + "/\"" + map.getContainerWorkspaceFolder() + "\"}");
                    arguments.set(i, positionalArgument);
                    index++;
                }
            }
            if (isFirst) {
                String helpArgs = "";
                for (int idx = 1; idx < inputSet.size(); idx++) {
                    helpArgs += "<product_" + idx + "> ";
                }
                final StringBuilder text = new StringBuilder();
                text.append(String.format(skeleton, Paths.get(unit.getScriptTargetPath()).getFileName(), helpArgs));
                for (int i = 1; i <= inputSet.size(); i++) {
                    text.append("\n$argument").append(i).append("=$").append(i);
                }
                text.append("\n");

                builder.insert(0, text);
            }
            if (isBash) {
                args.set(2, String.join(" ", arguments));
            }
        }
        appendLine(builder, comment);
        append(builder, "docker run ");
        append(builder, String.join(" ", container.getArguments()));
        if (volumeMap != null) {
            for (Map.Entry<String, String> entry : volumeMap.entrySet()) {
                append(builder, " -v " + entry.getKey() + ":" + entry.getValue());
            }
        }
        Map<String, String> env = container.getEnvironmentVariables();
        if (env != null) {
            for (Map.Entry<String, String> entry : env.entrySet()) {
                append(builder, " -e \"" + entry.getKey() + "=" + entry.getValue() + "\"");
            }
        }
        Long memory = unit.getMinMemory();
        if (memory != null && memory > 0) {
            append(builder, " --memory " + memory + "MB ");
        }
        String registry = container.getContainerRegistry();
        if (!StringUtilities.isNullOrEmpty(registry)) {
            append(builder, registry + "/");
        }
        append(builder, container.getContainerName());
        append(builder, " " + String.join(" ", args));
        appendLine(builder, null);
        return builder.toString();
    }

    private void append(final StringBuilder builder, String line) {
        if (line != null) {
            builder.append(line);
        }
    }

    private void appendLine(final StringBuilder builder, String line) {
        if (line != null) {
            builder.append(line);
        }
        builder.append("\n");
    }
}
