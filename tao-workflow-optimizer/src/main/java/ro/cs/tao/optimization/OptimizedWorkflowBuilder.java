package ro.cs.tao.optimization;

import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.RuntimeOptimizer;
import ro.cs.tao.execution.Optimizers;
import ro.cs.tao.persistence.exception.PersistenceException;
import ro.cs.tao.services.base.WorkflowBuilderBase;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;
import ro.cs.tao.workflow.enums.ComponentType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A OptimizedWorkflowBuilder is a helper class for creating a workflow from an optimization graph.
 *
 * @author Alexandru Pirlea
 */
public class OptimizedWorkflowBuilder extends WorkflowBuilderBase {

    @Override
    protected void addNodes(WorkflowDescriptor workflow) throws PersistenceException {
        /* do nothing */
    }

    @Override
    public String getName() {
        return "Optimized Workflow Builder";
    }

    @Override
    protected void addLink(WorkflowDescriptor workflow, WorkflowNodeDescriptor parent, WorkflowNodeDescriptor child) throws PersistenceException {
        super.addLink(workflow, parent, child);
    }

    public WorkflowNodeDescriptor createOptimizedNode(OptimizationNode node, WorkflowDescriptor workflow) throws PersistenceException {
        WorkflowNodeDescriptor optimizedNode = null;

        if (node.getComponentIds().size() == 0) {
            /* error: an optimization node cannot exit without a component */

        } else if (node.getComponentIds().size() == 1) {
            /* one node in list */
            optimizedNode = getNodeClone(node, workflow);

        } else {
            /* aggregate list */
            List<ProcessingComponent> components = new ArrayList<>();

            node.getComponentIds().forEach((id) ->
                                 components.add(node.getComponentDefinition().getComponent(id)));

            RuntimeOptimizer optimizer = Optimizers.findOptimizer(components.get(0).getContainerId());

            ProcessingComponent result = optimizer.aggregate(components.toArray(new ProcessingComponent[0]));

            optimizedNode = createNodeWithComponent(result, node, workflow);
        }

        return optimizedNode;
    }

    private WorkflowNodeDescriptor getNodeClone(OptimizationNode node, WorkflowDescriptor workflow) throws PersistenceException {
        WorkflowNodeDescriptor newNode = new WorkflowNodeDescriptor();
        WorkflowNodeDescriptor oldNode = node.getComponentDefinition().getWorkflowNode(node.getFirstComponentId());

        newNode.setWorkflow(workflow);
        newNode.setName(oldNode.getName());
        newNode.setxCoord(oldNode.getxCoord());
        newNode.setyCoord(oldNode.getyCoord());
        newNode.setComponentId(oldNode.getComponentId());
        newNode.setComponentType(oldNode.getComponentType());
        newNode.setCustomValues(oldNode.getCustomValues());
        newNode.setPreserveOutput(oldNode.getPreserveOutput());
        newNode.setCreated(LocalDateTime.now());
        newNode.setLevel(node.getLevel());

        newNode = workflowService.addNode(workflow.getId(), newNode);

        return newNode;
    }

    private WorkflowNodeDescriptor createNodeWithComponent(ProcessingComponent comp, OptimizationNode node, WorkflowDescriptor workflow) throws PersistenceException {
        WorkflowNodeDescriptor newNode = new WorkflowNodeDescriptor();
        WorkflowNodeDescriptor oldNode = node.getComponentDefinition().getWorkflowNode(node.getFirstComponentId());

        newNode.setName("Optimized component chain");

        newNode.setxCoord(oldNode.getxCoord());
        newNode.setyCoord(oldNode.getyCoord());

        newNode.setComponentId(comp.getId());
        newNode.setComponentType(ComponentType.PROCESSING);

        newNode.setCustomValues(oldNode.getCustomValues());
        newNode.setPreserveOutput(oldNode.getPreserveOutput());
        newNode.setCreated(LocalDateTime.now());
        newNode.setLevel(node.getLevel());

        newNode = workflowService.addNode(workflow.getId(), newNode);

        return newNode;
    }
}
