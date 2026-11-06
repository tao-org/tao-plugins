
package ro.cs.tao.utils.cwl;

import org.junit.Test;
import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.utils.*;
import java.util.*;
import static org.junit.Assert.*;

public class CWLConversionTest {
    private CWLTemplate load(String yaml) throws Exception {
        Object graph = RootLoader.loadDocument(yaml, "file:///fixtures/workflow.cwl");
        CWLTemplate template = new CWLTemplate();
        template.setCwlVersion("v1.2");
        template.setCwlMainGraphObject("$graph");
        if (graph instanceof Collection<?> values) template.addAllElements(values, "file:///fixtures/workflow.cwl");
        else template.addElement(graph, "file:///fixtures/workflow.cwl");
        return template;
    }

    @Test public void literalDefaultsRemainTypedAndAreNotSources() {
        WorkflowStepInput input = new WorkflowStepInputImpl();
        input.setId(Optional.of("count"));
        input.setDefault(Optional.of(42));
        Map<Object, Object> saved = ((Saveable) input).save();
        assertEquals(42, saved.get("default"));
        assertFalse(saved.containsKey("source"));
        input.setDefault(Optional.of(Map.of("class", "File", "path", "/tmp/a#b")));
        assertEquals(Map.of("class", "File", "path", "/tmp/a#b"), ((Saveable) input).save().get("default"));
    }

    @Test public void sourceListsAndBooleansRemainTyped() {
        WorkflowStepInput input = new WorkflowStepInputImpl();
        input.setId(Optional.of("files"));
        input.setSource(new LinkedList<>(List.of("a/out", "b/out")));
        input.setLoadContents(Optional.of(false));
        Map<Object, Object> saved = ((Saveable) input).save();
        assertEquals(List.of("a/out", "b/out"), saved.get("source"));
        assertEquals(false, saved.get("loadContents"));
    }

    @Test public void emptyOptionalAndLinkedListsSerialize() {
        CommandLineTool tool = new CommandLineToolImpl();
        tool.setClass_(CommandLineTool_class.COMMANDLINETOOL);
        tool.setId(Optional.of("tool"));
        tool.setBaseCommand(new LinkedList<>(List.of("echo")));
        tool.setLabel(Optional.empty());
        assertEquals(List.of("echo"), ((Saveable) tool).save().get("baseCommand"));
        assertFalse(((Saveable) tool).save().containsKey("label"));
    }

    @Test public void unionTypesAndArraySchemasRoundTrip() throws Exception {
        CWLTemplate template = load("""
                cwlVersion: v1.2
                class: CommandLineTool
                id: tool
                baseCommand: echo
                inputs:
                  choice:
                    type: ['null', string, int]
                    default: 3
                  files:
                    type: {type: array, items: File}
                outputs: {}
                """);
        Map<Object, Object> saved = template.save();
        Map<?, ?> tool = (Map<?, ?>) ((List<?>) saved.get("$graph")).getFirst();
        List<?> inputs = (List<?>) tool.get("inputs");
        assertEquals(List.of("null", "string", "int"), ((Map<?, ?>) inputs.getFirst()).get("type"));
        assertTrue(((Map<?, ?>) inputs.get(1)).get("type") instanceof Map<?, ?>);
        RootLoader.loadDocument((Map<String, Object>) (Map<?, ?>) saved, "file:///fixtures/result.cwl");
    }

    @Test public void emptyInterfacesImportAndScalarBaseCommandIsDetected() throws Exception {
        CWLTemplate template = load("""
                cwlVersion: v1.2
                class: CommandLineTool
                baseCommand: echo
                inputs: {}
                outputs: {}
                """);
        CommandLineTool tool = (CommandLineTool) template.getCwlMainGraphObject("$graph").getFirst();
        assertEquals("echo", CWLAppDetector.createApplicationTemplate(tool, null).getFirst().getApplicationName());
        assertFalse(tool.getId().get().isBlank());
    }

    @Test public void normalizationDoesNotTouchLiteralUrisOrDefaults() throws Exception {
        CWLTemplate template = load("""
                cwlVersion: v1.2
                class: CommandLineTool
                id: tool
                baseCommand: echo
                inputs:
                  url:
                    type: string
                    default: 'file:///tmp/input#fragment'
                outputs: {}
                """);
        CommandLineTool tool = (CommandLineTool) template.getCwlMainGraphObject("$graph").getFirst();
        assertEquals("file:///tmp/input#fragment", ((CommandInputParameter) tool.getInputs().getFirst()).getDefault().get());
    }

    @Test public void externalRunUsesDocumentAndSelectedFragment() throws Exception {
        CWLTemplate template = load("""
                cwlVersion: v1.2
                class: Workflow
                id: main
                inputs: {}
                outputs: {}
                steps:
                  chosen: {run: tools/tool.cwl#second, in: {}, out: []}
                """);
        Object tools = RootLoader.loadDocument("""
                cwlVersion: v1.2
                $graph:
                  - {id: first, class: CommandLineTool, baseCommand: echo, inputs: {}, outputs: {}}
                  - {id: second, class: CommandLineTool, baseCommand: printf, inputs: {}, outputs: {}}
                """, "file:///fixtures/tools/tool.cwl");
        template.addAllElements((Collection<?>) tools, "file:///fixtures/tools/tool.cwl");
        template.resolveRuns();
        Workflow workflow = (Workflow) template.getCwlGraphChild("$graph", "main");
        CommandLineTool selected = (CommandLineTool) template.getCwlGraphChild("$graph", ((WorkflowStep) workflow.getSteps().getFirst()).getRun().toString());
        assertEquals("printf", selected.getBaseCommand());
    }

    @Test public void externalToolWithoutExplicitIdResolvesByDocumentUri() throws Exception {
        CWLTemplate template = load("""
                cwlVersion: v1.2
                class: Workflow
                id: main
                inputs: {}
                outputs: {}
                steps:
                  tool: {run: tools/plain.cwl, in: {}, out: []}
                """);
        String uri = "file:///fixtures/tools/plain.cwl";
        Object tool = RootLoader.loadDocument("cwlVersion: v1.2\nclass: CommandLineTool\nbaseCommand: echo\ninputs: {}\noutputs: {}\n", uri);
        template.addElement(tool, uri);
        template.resolveRuns();
        Workflow workflow = (Workflow) template.getCwlGraphChild("$graph", "main");
        assertNotNull(template.getCwlGraphChild("$graph", ((WorkflowStep) workflow.getSteps().getFirst()).getRun().toString()));
    }

    @Test public void duplicateLocalIdsAcrossDocumentsStayDistinct() throws Exception {
        CWLTemplate template = load("""
                cwlVersion: v1.2
                class: Workflow
                id: main
                inputs: {}
                outputs: {}
                steps:
                  a: {run: a/tool.cwl#tool, in: {}, out: []}
                  b: {run: b/tool.cwl#tool, in: {}, out: []}
                """);
        for (String directory : List.of("a", "b")) {
            String uri = "file:///fixtures/" + directory + "/tool.cwl";
            Object tool = RootLoader.loadDocument("cwlVersion: v1.2\nclass: CommandLineTool\nid: tool\nbaseCommand: echo\ninputs: {}\noutputs: {}\n", uri);
            template.addElement(tool, uri);
        }
        template.resolveRuns();
        Workflow workflow = (Workflow) template.getCwlGraphChild("$graph", "main");
        assertNotEquals(((WorkflowStep) workflow.getSteps().get(0)).getRun(), ((WorkflowStep) workflow.getSteps().get(1)).getRun());
    }

    @Test public void ambiguousUnfragmentedGraphReferenceFails() throws Exception {
        CWLTemplate template = load("""
                cwlVersion: v1.2
                $graph:
                  - {id: main, class: Workflow, inputs: {}, outputs: {}, steps: {a: {run: workflow.cwl, in: {}, out: []}}}
                  - {id: tool, class: CommandLineTool, baseCommand: echo, inputs: {}, outputs: {}}
                """);
        assertThrows(IllegalArgumentException.class, template::resolveRuns);
    }

    private static final String NESTED = """
            cwlVersion: v1.2
            $graph:
              - id: main
                class: Workflow
                requirements: {SubworkflowFeatureRequirement: {}}
                inputs: {}
                outputs:
                  final: {type: string, outputSource: right/result}
                steps:
                  left: {run: '#child', in: {value: {default: left}}, out: [result]}
                  right: {run: '#child', in: {value: {source: left/result}}, out: [result]}
              - id: child
                class: Workflow
                inputs: {value: string}
                outputs:
                  result: {type: string, outputSource: inner/result}
                steps:
                  inner: {run: '#tool', in: {value: value}, out: [result]}
              - id: tool
                class: CommandLineTool
                baseCommand: echo
                inputs:
                  value: {type: string, inputBinding: {position: 1}}
                outputs:
                  result: {type: string, outputBinding: {glob: result.txt}}
            """;

    @Test public void repeatedSubworkflowsHaveDistinctScopedStepsAndCorrectLinks() throws Exception {
        CWLTemplate template = load(NESTED);
        CWLGraphPreparation.prepare(template);
        Workflow flat = (Workflow) template.getCwlGraphChild("$graph", "main");
        assertEquals(2, flat.getSteps().size());
        WorkflowStep right = flat.getStep("right__inner");
        WorkflowStepInput input = (WorkflowStepInput) right.getIn().getFirst();
        assertEquals("left__inner/result", input.getSource());
        WorkflowStep left = flat.getStep("left__inner");
        assertNotEquals(left.getRun(), right.getRun());
        assertEquals("left", ((WorkflowStepInput) left.getIn().getFirst()).getDefault().get());
        assertEquals("right__inner/result", ((WorkflowOutputParameter) flat.getOutputs().getFirst()).getOutputSource());
        RootLoader.loadDocument((Map<String, Object>) (Map<?, ?>) template.save(), "file:///fixtures/result.cwl");
    }

    @Test public void inheritedDockerRequirementReachesEachExpandedTool() throws Exception {
        CWLTemplate template = load(NESTED.replace("requirements: {SubworkflowFeatureRequirement: {}}",
                "requirements: {SubworkflowFeatureRequirement: {}, DockerRequirement: {dockerPull: 'example/tool:1'}}"));
        CWLGraphPreparation.prepare(template);
        for (Object object : template.getCwlMainGraphObject("$graph")) {
            if (object instanceof CommandLineTool tool)
                assertEquals("example/tool:1", CWLUtils.findDockerReq(tool).getDockerPull().get());
        }
    }

    @Test public void scatterIsRejectedBeforeImport() throws Exception {
        CWLTemplate template = load(NESTED.replace("left: {run: '#child',", "left: {scatter: value, run: '#child',"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> CWLGraphPreparation.prepare(template));
        assertTrue(error.getMessage().contains("scatter"));
    }

    @Test public void conditionalExecutionIsRejectedBeforeImport() throws Exception {
        CWLTemplate template = load(NESTED.replace("left: {run: '#child',", "left: {when: '$(true)', run: '#child',"));
        assertThrows(IllegalArgumentException.class, () -> CWLGraphPreparation.prepare(template));
    }

    @Test public void cyclicDependenciesAreRejected() throws Exception {
        CWLTemplate template = load(NESTED.replace("value: {default: left}", "value: {source: right/result}"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> CWLGraphPreparation.prepare(template));
        assertTrue(error.getMessage().contains("Cyclic"));
    }
}
