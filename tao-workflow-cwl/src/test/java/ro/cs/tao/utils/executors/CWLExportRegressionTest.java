
package ro.cs.tao.utils.executors;

import org.junit.Test;
import org.w3id.cwl.cwl1_2.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class CWLExportRegressionTest {
    private ExecutionUnit unit() {
        return new ExecutionUnit(ExecutorType.MOCK, "localhost", null, null, List.of(), false, null);
    }
    private CWLExportExecutor exporter(ExecutionUnit unit) {
        return new CWLExportExecutor(unit, null, null);
    }
    @Test public void idsRetainUnderscoresAndIgnoreLabels() {
        assertEquals("component_with_spaces_42", CWLExportExecutor.stepId("Component_with spaces", 42L));
    }
    @Test public void ordinaryValuesPreservePathsAndUrls() {
        for (String value : List.of("https://server/a/b#c", "/data/a/b", "a/b"))
            assertEquals(value, CWLExportExecutor.yamlParameterValue(value, false));
        assertEquals("result.tif", CWLExportExecutor.yamlParameterValue("/work/result.tif", true));
    }
    @Test public void diskAndRamAreSeparateResourceBounds() throws Exception {
        ExecutionUnit unit = unit();
        unit.setMinDisk(2048L);
        unit.setMinMemory(1024L);
        Method method = CWLExportExecutor.class.getDeclaredMethod("createReqListForCommandToolSteps");
        method.setAccessible(true);
        ResourceRequirement resource = (ResourceRequirement) ((List<?>) method.invoke(exporter(unit))).getFirst();
        assertEquals(1024L, resource.getRamMin());
        assertEquals(2048L, resource.getOutdirMin());
        unit.setMinMemory(null);
        resource = (ResourceRequirement) ((List<?>) method.invoke(exporter(unit))).getFirst();
        assertNull(resource.getRamMin());
        assertEquals(2048L, resource.getOutdirMin());
    }
    @Test public void writeFailuresPropagateAndDoNotCreatePartialGraphs() throws Exception {
        Path directory = Files.createTempDirectory("cwl-export-test");
        try {
            Path target = directory.resolve("missing").resolve("workflow.cwl");
            Method method = CWLExportExecutor.class.getDeclaredMethod("writeToFile", Path.class, Map.class);
            method.setAccessible(true);
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                    () -> method.invoke(exporter(unit()), target, Map.of("cwlVersion", "v1.2")));
            assertTrue(failure.getCause() instanceof java.io.UncheckedIOException);
            assertFalse(Files.exists(target));
        } finally { Files.delete(directory); }
    }
    @Test public void graphFilesUseTypedYamlAndReplaceAtomically() throws Exception {
        Path directory = Files.createTempDirectory("cwl-export-test");
        Path target = directory.resolve("workflow.cwl");
        try {
            Method method = CWLExportExecutor.class.getDeclaredMethod("writeToFile", Path.class, Map.class);
            method.setAccessible(true);
            method.invoke(exporter(unit()), target,
                    new LinkedHashMap<>(Map.of("count", 3, "source", List.of("a/out", "b/out"), "flag", false)));
            Map<?, ?> actual = new tools.jackson.databind.ObjectMapper(new ro.cs.tao.utils.yaml.CWLYAMLFactory())
                    .readValue(target.toFile(), Map.class);
            assertEquals(3, actual.get("count"));
            assertEquals(false, actual.get("flag"));
            assertEquals(List.of("a/out", "b/out"), actual.get("source"));
            try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        } finally { Files.deleteIfExists(target); Files.delete(directory); }
    }
}
