package ro.cs.tao.optimization;

import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;

import java.util.HashMap;
import java.util.Map;

/**
 * A ComponentsDefinition class is a helper class with dependencies from a component id to a processing
 * component and a workflow node.
 *
 * @author Alexandru Pirlea
 */
public class ComponentsDefinition {
    private Map<String, ProcessingComponent> idToComp = new HashMap<>();
    private Map<String, WorkflowNodeDescriptor> idToWorkflowNode = new HashMap<>();

    public void addComponent(ProcessingComponent comp, String id) {
        idToComp.put(id, comp);
    }

    public ProcessingComponent getComponent(String id) {
        return idToComp.get(id);
    }

    public void addWorkflowNode(WorkflowNodeDescriptor node, String id) {
        idToWorkflowNode.put(id, node);
    }

    public WorkflowNodeDescriptor getWorkflowNode(String id) {
        return idToWorkflowNode.get(id);
    }
}
