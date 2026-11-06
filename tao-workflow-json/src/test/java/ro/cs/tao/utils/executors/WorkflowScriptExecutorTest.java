package ro.cs.tao.utils.executors;

import org.junit.Test;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.utils.executors.container.ContainerType;
import ro.cs.tao.utils.executors.container.ContainerUnit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;

public class WorkflowScriptExecutorTest {
    private ExecutionUnit unit(Path path, String id, List<String> deps, List<String> args) {
        ExecutionUnit unit = new ExecutionUnit(ExecutorType.SCRIPT, "localhost", null, null, args, false, null);
        unit.addMetadata("scriptPath", path.toString());
        unit.addMetadata("id", id);
        unit.addMetadata("dependsOn", deps);
        return unit;
    }
    private WorkflowScriptExecutor exporter(boolean python, ExecutionUnit unit) {
        return python ? new PythonExecutor(unit) : new BashExecutor(unit);
    }
    private ContainerUnit container() {
        ContainerUnit container = new ContainerUnit(ContainerType.DOCKER);
        container.setContainerName("image:1");
        container.setContainerRegistry("registry/");
        container.setArguments(List.of("--rm"));
        container.addVolumeMapping("/host space", "/data");
        container.addEnvironmentVariable("VALUE", "quote'\" $(touch injected) `false`\nline");
        return container;
    }
    @Test public void capturesDockerTokensWithoutMutatingArguments() {
        List<String> args = List.of("/bin/bash", "-c", "printf '%s' \"$1\"", "--", "a b");
        ExecutionUnit unit = unit(Path.of("unused"), "1", List.of(), args);
        unit.setContainerUnit(container());
        List<String> command = WorkflowScriptExecutor.command(unit);
        assertEquals(args, command.subList(command.size() - args.size(), command.size()));
        assertTrue(command.contains("/host space:/data"));
        assertTrue(command.contains("registry/image:1"));
        assertSame(args, unit.getArguments());
    }
    @Test public void supportsEmptyContainerOptions() {
        ExecutionUnit unit = unit(Path.of("unused"), "1", List.of(), List.of("echo", ""));
        ContainerUnit container = new ContainerUnit(ContainerType.DOCKER);
        container.setContainerName("image");
        unit.setContainerUnit(container);
        assertEquals(List.of("docker", "run", "image", "echo", ""), WorkflowScriptExecutor.command(unit));
    }
    @Test public void preservesInheritedEnvironmentAndExplicitMemoryLimit() {
        ExecutionUnit unit = unit(Path.of("unused"), "1", List.of(), List.of("echo"));
        ContainerUnit container = container();
        container.addEnvironmentVariable("INHERITED", null);
        container.setArguments(List.of("--memory", "512m"));
        unit.setContainerUnit(container);
        unit.setMinMemory(1024L);
        List<String> command = WorkflowScriptExecutor.command(unit);
        assertTrue(command.contains("INHERITED"));
        assertFalse(command.contains("INHERITED=null"));
        assertEquals(1, Collections.frequency(command, "--memory"));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsUnsupportedContainer() {
        ExecutionUnit unit = unit(Path.of("unused"), "1", List.of(), List.of("echo"));
        unit.setContainerUnit(new ContainerUnit(ContainerType.KUBERNETES));
        WorkflowScriptExecutor.command(unit);
    }
    @Test public void converterRegistration() {
        Set<Class<?>> types = new HashSet<>();
        ServiceLoader.load(ExecutionDescriptorConverter.class).forEach(value -> types.add(value.getClass()));
        assertTrue(types.contains(PythonExecutor.class));
        assertTrue(new PythonExecutor().isIntendedFor(ExecutionUnitFormat.PYTHON));
        assertTrue(new BashExecutor().isIntendedFor(ExecutionUnitFormat.BASH));
    }
    @Test public void refusesToAppendToUnrelatedFile() throws Exception {
        Path path = Files.createTempFile("workflow", ".py");
        try {
            Files.writeString(path, "user script");
            try {
                new PythonExecutor(unit(path, "1", List.of(), List.of("echo"))).execute(false);
                fail("Expected incompatible script rejection");
            } catch (IllegalArgumentException expected) {
                assertEquals("user script", Files.readString(path));
            }
        } finally { Files.delete(path); }
    }
    @Test public void generatesRuntimeFixtures() throws Exception {
        Path dir = Path.of("target", "workflow-script-fixtures");
        Files.createDirectories(dir);
        for (boolean python : List.of(false, true)) {
            String ext = python ? ".py" : ".sh";
            Path diamond = dir.resolve("diamond" + ext);
            Files.deleteIfExists(diamond);
            for (String id : List.of("4", "3", "2", "1")) {
                List<String> deps = id.equals("4") ? List.of("2", "3")
                                    : id.equals("1") ? List.of() : List.of("1");
                ExecutionUnit branch = unit(diamond, id, deps, List.of(id));
                branch.setContainerUnit(container());
                exporter(python, branch).execute(false);
            }
            for (String scenario : List.of("ordered", "failure", "missing", "cycle", "duplicate", "native")) {
                Path path = dir.resolve(scenario + ext);
                Files.deleteIfExists(path);
                if (scenario.equals("native")) {
                    ExecutionUnit nativeUnit = unit(path, "1", List.of(), List.of("/bin/bash", "-c",
                            "printf '%s\\n' \"$1\"; pwd", "--", "a 'quoted' $literal\nsecond line"));
                    nativeUnit.setWorkingDirectory("/tmp");
                    exporter(python, nativeUnit).execute(false);
                    continue;
                }
                List<String> args = List.of("/bin/bash", "-c", "printf '%s' \"$1\"", "--", "a 'quoted' $literal\nline", "", "λ");
                ExecutionUnit child = unit(path, "2", List.of("1"), args);
                child.setContainerUnit(container());
                exporter(python, child).execute(false);
                if (!scenario.equals("missing")) {
                    ExecutionUnit parent = unit(path, scenario.equals("duplicate") ? "2" : "1",
                            scenario.equals("cycle") ? List.of("2") : List.of(),
                            List.of(scenario.equals("failure") ? "FAIL" : "parent"));
                    parent.setContainerUnit(container());
                    exporter(python, parent).execute(false);
                }
                String script = Files.readString(path);
                assertEquals(1, script.split(python ? "if __name__" : "# Execute only", -1).length - 1);
                assertEquals(args, child.getArguments());
            }
        }
    }
}
