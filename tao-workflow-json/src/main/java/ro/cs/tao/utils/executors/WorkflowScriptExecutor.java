package ro.cs.tao.utils.executors;

import ro.cs.tao.utils.executors.container.ContainerType;
import ro.cs.tao.utils.executors.container.ContainerUnit;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Captures resolved workflow commands without executing them. */
abstract class WorkflowScriptExecutor extends Executor<Object> implements ExecutionDescriptorConverter {
    private static final Object WRITE_LOCK = new Object();
    protected ExecutionUnit unit;
    protected WorkflowScriptExecutor() { }
    protected WorkflowScriptExecutor(ExecutionUnit unit) {
        super(unit.getHost(), unit.getArguments(), unit.asSuperUser());
        this.unit = unit;
    }
    public void setUnit(ExecutionUnit unit) { this.unit = unit; }
    @Override public boolean canConnect() { return true; }
    protected abstract String header();
    protected abstract String footer();
    protected abstract String step(String id, List<String> dependencies, List<String> command, String cwd);

    @Override
    public int execute(boolean logMessages) throws Exception {
        Objects.requireNonNull(unit, "Execution unit not set");
        Path path = Path.of(Objects.requireNonNull(unit.getScriptTargetPath(), "Script target path not set"));
        Map<String, Object> metadata = unit.getMetadata();
        String id = identifier(metadata.getOrDefault("id", UUID.randomUUID().toString()));
        List<String> dependencies = new ArrayList<>();
        if (metadata.get("dependsOn") instanceof Iterable<?> values) {
            for (Object value : values) dependencies.add(identifier(value));
        }
        String text = step(id, dependencies, command(unit),
                           unit.getContainerUnit() == null ? unit.getWorkingDirectory() : null);
        // Executors are instantiated per task; serialize read/insert/write across instances.
        synchronized (WRITE_LOCK) {
            String previous = Files.exists(path) && Files.size(path) > 0
                              ? Files.readString(path, StandardCharsets.UTF_8) : header() + footer();
            if (!previous.startsWith(header()) || !previous.endsWith(footer()))
                throw new IllegalArgumentException("Not a compatible workflow script: " + path);
            Files.writeString(path, previous.substring(0, previous.length() - footer().length()) + text + footer(),
                              StandardCharsets.UTF_8);
        }
        if (outputConsumer != null) outputConsumer.consume(text);
        return 0;
    }

    private static String identifier(Object value) {
        String id = Objects.requireNonNull(value, "Task id").toString();
        if (!id.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Invalid task id: " + id);
        return id;
    }

    static List<String> command(ExecutionUnit unit) {
        List<String> result = new ArrayList<>();
        ContainerUnit container = unit.getContainerUnit();
        if (container != null) {
            if (container.getType() != ContainerType.DOCKER)
                throw new IllegalArgumentException("Workflow scripts support Docker containers only");
            result.add("docker"); result.add("run");
            if (container.getArguments() != null) result.addAll(container.getArguments());
            if (container.getVolumeMap() != null) container.getVolumeMap().forEach((source, target) -> {
                result.add("-v"); result.add(source + ":" + target);
            });
            if (container.getEnvironmentVariables() != null) container.getEnvironmentVariables().forEach((key, value) -> {
                result.add("-e"); result.add(value == null ? key : key + "=" + value);
            });
            if (unit.getMinMemory() != null && unit.getMinMemory() > 0
                && (container.getArguments() == null || container.getArguments().stream()
                    .noneMatch(arg -> "--memory".equals(arg) || "-m".equals(arg) || arg.startsWith("--memory=")))) {
                result.add("--memory"); result.add(unit.getMinMemory() + "m");
            }
            if (unit.getWorkingDirectory() != null && !unit.getWorkingDirectory().isEmpty()) {
                result.add("--workdir"); result.add(unit.getWorkingDirectory());
            }
            String image = Objects.requireNonNull(container.getContainerName(), "Container image not set");
            String registry = container.getContainerRegistry();
            result.add(registry == null || registry.isBlank() ? image : registry.replaceAll("/+$", "") + "/" + image);
        }
        if (unit.getArguments() == null || unit.getArguments().isEmpty())
            throw new IllegalArgumentException("Task command not set");
        result.addAll(unit.getArguments());
        result.forEach(value -> {
            if (Objects.requireNonNull(value, "Null command argument").indexOf('\0') >= 0)
                throw new IllegalArgumentException("NUL in command argument");
        });
        return result;
    }
}
