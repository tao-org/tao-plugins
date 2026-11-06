
package ro.cs.tao.utils.cwl;

import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.utils.CWLSerializer;
import org.w3id.cwl.cwl1_2.utils.RootLoader;

import java.util.*;

import static ro.cs.tao.utils.cwl.CWLConstants.CWL_GRAPH_ID;

/** Validates and expands workflow invocations before any TAO persistence takes place. */
public final class CWLGraphPreparation {
    private final CWLTemplate template;
    private final List<Object> tools = new ArrayList<>();
    private final List<Object> steps = new ArrayList<>();
    private final Set<String> stack = new HashSet<>();

    private CWLGraphPreparation(CWLTemplate template) { this.template = template; }

    public static void prepare(CWLTemplate template) throws IllegalAccessException {
        template.resolveRuns();
        CWLGraphPreparation preparation = new CWLGraphPreparation(template);
        preparation.expand();
    }

    private void expand() throws IllegalAccessException {
        List<Workflow> workflows = template.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(Workflow.class::isInstance).map(Workflow.class::cast).toList();
        if (workflows.isEmpty()) {
            for (Object object : template.getCwlMainGraphObject(CWL_GRAPH_ID)) validateProcess(object);
            return;
        }
        Set<String> referenced = new HashSet<>();
        for (Workflow workflow : workflows) for (Object item : workflow.getSteps())
            referenced.add(((WorkflowStep) item).getRun().toString());
        List<Workflow> roots = workflows.stream().filter(w -> !referenced.contains(w.getId().get())).toList();
        Workflow root = roots.stream().filter(w -> w.getId().get().equals("main")).findFirst().orElse(null);
        if (root == null && roots.size() == 1) root = roots.getFirst();
        if (root == null) throw new IllegalArgumentException("CWL must have one unambiguous entry workflow (or #main).");
        Map<String, Map<String, Object>> inputs = new LinkedHashMap<>();
        for (Object item : root.getInputs()) {
            WorkflowInputParameter input = (WorkflowInputParameter) item;
            inputs.put(input.getId().get(), new LinkedHashMap<>(Map.of("source", input.getId().get())));
        }
        Map<String, Object> outputs = expandWorkflow(root, "", inputs, List.of());
        Map<Object, Object> flat = CWLSerializer.serialize(root);
        flat.put("steps", steps);
        List<Object> flatOutputs = new ArrayList<>();
        for (Object item : root.getOutputs()) {
            WorkflowOutputParameter output = (WorkflowOutputParameter) item;
            Map<Object, Object> value = CWLSerializer.serialize(output);
            value.put("outputSource", outputs.get(output.getId().get()));
            flatOutputs.add(value);
        }
        flat.put("outputs", flatOutputs);
        List<Object> graph = new ArrayList<>();
        graph.add(flat); graph.addAll(tools);
        Object loaded = RootLoader.loadDocument(new LinkedHashMap<>(Map.of("cwlVersion", "v1.2", "$graph", graph)),
                "file:///tao-conversion.cwl");
        template.setCwlMainGraphObject(CWL_GRAPH_ID);
        template.addAllElements((Collection<?>) loaded, "file:///tao-conversion.cwl");
        template.resolveRuns();
    }

    private Map<String, Object> expandWorkflow(Workflow workflow, String prefix,
            Map<String, Map<String, Object>> inputs, List<Object> inheritedRequirements) {
        inheritedRequirements = mergeRequirements(inheritedRequirements, CWLSerializer.serialize(workflow).get("requirements"));
        String workflowId = workflow.getId().get();
        if (!stack.add(workflowId)) throw new IllegalArgumentException("Recursive CWL workflow: " + workflowId);
        try {
            Map<String, WorkflowStep> localSteps = new LinkedHashMap<>();
            for (Object item : workflow.getSteps()) {
                WorkflowStep step = (WorkflowStep) item;
                if (localSteps.put(step.getId().get(), step) != null)
                    throw new IllegalArgumentException("Duplicate CWL step: " + step.getId().get());
            }
            Map<String, Map<String, Object>> expanded = new LinkedHashMap<>();
            Set<String> active = new HashSet<>();
            for (String id : localSteps.keySet()) expandStep(id, prefix, inputs, localSteps, expanded, active, inheritedRequirements);
            Map<String, Object> outputs = new LinkedHashMap<>();
            for (Object item : workflow.getOutputs()) {
                WorkflowOutputParameter output = (WorkflowOutputParameter) item;
                outputs.put(output.getId().get(), resolve(output.getOutputSource(), prefix, inputs, localSteps, expanded, active, inheritedRequirements));
            }
            return outputs;
        } finally { stack.remove(workflowId); }
    }

    private Map<String, Object> expandStep(String id, String prefix, Map<String, Map<String, Object>> inputs,
            Map<String, WorkflowStep> localSteps, Map<String, Map<String, Object>> expanded, Set<String> active, List<Object> inheritedRequirements) {
        if (expanded.containsKey(id)) return expanded.get(id);
        WorkflowStep step = localSteps.get(id);
        if (step == null) throw new IllegalArgumentException("Unknown CWL step: " + id);
        if (!active.add(id)) throw new IllegalArgumentException("Cyclic CWL step dependency: " + id);
        try {
            Map<Object, Object> stepMap = CWLSerializer.serialize(step);
            reject(stepMap, "scatter", "scatterMethod", "when");
            String instanceId = prefix + id;
            Map<String, Map<String, Object>> bindings = new LinkedHashMap<>();
            List<Object> flatInputs = new ArrayList<>();
            for (Object item : step.getIn()) {
                WorkflowStepInput input = (WorkflowStepInput) item;
                Map<Object, Object> value = CWLSerializer.serialize(input);
                reject(value, "linkMerge", "pickValue", "valueFrom");
                Map<String, Object> binding = new LinkedHashMap<>();
                if (input.getSource() != null) {
                    Object source = resolve(input.getSource(), prefix, inputs, localSteps, expanded, active, inheritedRequirements);
                    if (source instanceof Map<?, ?> literal) literal.forEach((key, v) -> binding.put(key.toString(), v));
                    else binding.put("source", source);
                }
                if (input.getDefault() != null && input.getDefault().isPresent()) binding.putIfAbsent("default", input.getDefault().get());
                bindings.put(input.getId().get(), binding);
                Map<String, Object> flatInput = new LinkedHashMap<>(binding);
                flatInput.put("id", input.getId().get());
                flatInputs.add(flatInput);
            }
            Object process = template.getCwlGraphChild(CWL_GRAPH_ID, step.getRun().toString());
            validateProcess(process);
            Map<String, Object> outputs = new LinkedHashMap<>();
            if (process instanceof Workflow child) {
                for (Object item : child.getInputs()) {
                    WorkflowInputParameter input = (WorkflowInputParameter) item;
                    if (!bindings.containsKey(input.getId().get()) && input.getDefault() != null && input.getDefault().isPresent())
                        bindings.put(input.getId().get(), new LinkedHashMap<>(Map.of("default", input.getDefault().get())));
                }
                outputs.putAll(expandWorkflow(child, instanceId + "__", bindings, mergeRequirements(inheritedRequirements, stepMap.get("requirements"))));
            } else {
                CommandLineTool tool = (CommandLineTool) process;
                Map<Object, Object> toolMap = CWLSerializer.serialize(tool);
                String toolId = "tool__" + instanceId;
                toolMap.put("id", toolId);
                List<Object> requirements = mergeRequirements(mergeRequirements(inheritedRequirements, stepMap.get("requirements")), toolMap.get("requirements"));
                if (!requirements.isEmpty()) toolMap.put("requirements", requirements);
                // Each invocation owns its defaults and interface; shared tools cannot overwrite each other.
                List<Object> toolInputs = new ArrayList<>();
                for (Object item : tool.getInputs()) {
                    CommandInputParameter input = (CommandInputParameter) item;
                    Map<Object, Object> value = CWLSerializer.serialize(input);
                    Map<String, Object> binding = bindings.get(input.getId().get());
                    if (binding != null && binding.containsKey("default")) value.put("default", binding.get("default"));
                    toolInputs.add(value);
                }
                toolMap.put("inputs", toolInputs);
                tools.add(toolMap);
                stepMap.put("id", instanceId);
                stepMap.put("run", "#" + toolId);
                stepMap.put("in", flatInputs);
                steps.add(stepMap);
                for (Object item : tool.getOutputs()) {
                    CommandOutputParameter output = (CommandOutputParameter) item;
                    outputs.put(output.getId().get(), instanceId + "/" + output.getId().get());
                }
            }
            expanded.put(id, outputs);
            return outputs;
        } finally { active.remove(id); }
    }

    private Object resolve(Object source, String prefix, Map<String, Map<String, Object>> inputs,
            Map<String, WorkflowStep> localSteps, Map<String, Map<String, Object>> expanded, Set<String> active, List<Object> inheritedRequirements) {
        if (source instanceof Collection<?> values) {
            List<Object> result = new ArrayList<>();
            for (Object item : values) result.add(resolve(item, prefix, inputs, localSteps, expanded, active, inheritedRequirements));
            // TAO links do not encode CWL nested-list merge semantics.
            if (result.size() != 1) throw new IllegalArgumentException("Multiple CWL sources require merge semantics unsupported by TAO.");
            return result.getFirst();
        }
        String reference = Objects.toString(source, "");
        if (inputs.containsKey(reference)) {
            Map<String, Object> binding = inputs.get(reference);
            return binding.containsKey("source") ? binding.get("source") : binding;
        }
        int slash = reference.indexOf('/');
        if (slash < 0) throw new IllegalArgumentException("Unknown CWL input: " + reference);
        Object resolved = expandStep(reference.substring(0, slash), prefix, inputs, localSteps, expanded, active, inheritedRequirements)
                .get(reference.substring(slash + 1));
        if (resolved == null) throw new IllegalArgumentException("Unknown CWL output: " + reference);
        return resolved;
    }

    private static List<Object> mergeRequirements(List<Object> inherited, Object local) {
        Map<String, Object> requirements = new LinkedHashMap<>();
        for (Object item : inherited) {
            Map<?, ?> requirement = (Map<?, ?>) item;
            requirements.put(requirement.get("class").toString(), item);
        }
        if (local instanceof Collection<?> values) {
            for (Object item : values) {
                Map<?, ?> requirement = (Map<?, ?>) item;
                String name = requirement.get("class").toString();
                // Workflow features are not requirements of a CommandLineTool.
                if (!name.endsWith("FeatureRequirement") && !name.equals("StepInputExpressionRequirement"))
                    requirements.put(name, item);
            }
        }
        return new ArrayList<>(requirements.values());
    }

    private static void validateProcess(Object process) {
        if (!(process instanceof Workflow) && !(process instanceof CommandLineTool))
            throw new IllegalArgumentException("TAO cannot import CWL process: " + (process == null ? "unresolved" : process.getClass().getSimpleName()));
    }
    private static void reject(Map<?, ?> value, String... keys) {
        for (String key : keys) if (value.containsKey(key))
            throw new IllegalArgumentException("CWL " + key + " is not representable in the TAO workflow model.");
    }
}
