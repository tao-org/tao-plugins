package ro.cs.tao.utils.executors;

import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.utils.executors.container.ContainerUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BashExecutor extends Executor<Object>
                            implements ExecutionDescriptorConverter {
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
        final String cmdLine = asCommandLine(this.unit);
        this.outputConsumer.consume(cmdLine);
        if (unit.getScriptTargetPath() != null) {
            try {
                final Path path = Paths.get(unit.getScriptTargetPath());
                if (!Files.exists(path)) {
                    Files.write(path, ("#!/bin/bash\n" + cmdLine).getBytes(), StandardOpenOption.CREATE);
                } else {
                    Files.write(path, cmdLine.getBytes(), StandardOpenOption.APPEND);
                }
            } catch (IOException e) {
                logger.severe(ExceptionUtils.getStackTrace(logger, e));
            }
        }
        return 0;
    }

    @Override
    public boolean canConnect() {
        return true;
    }

    private String asCommandLine(ExecutionUnit unit) {
        final StringBuilder builder = new StringBuilder();
        final ContainerUnit container = unit.getContainerUnit();
        final Map<String, Object> metadata = unit.getMetadata();
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
        }
        if (container != null) {
            appendLine(builder, comment);
            append(builder, "docker run ");
            append(builder, String.join(" ", container.getArguments()));
            Map<String, String> volumeMap = container.getVolumeMap();
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
                append(builder, " --memory " + memory + "MB");
            }
            String registry = container.getContainerRegistry();
            if (!StringUtilities.isNullOrEmpty(registry)) {
                append(builder, " " + registry + "/");
            }
            append(builder, container.getContainerName());
            append(builder, " " + String.join(" ", unit.getArguments()));
        }
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
