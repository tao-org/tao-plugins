package ro.cs.tao.optimization;

import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.RuntimeOptimizer;
import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.execution.Optimizers;
import ro.cs.tao.persistence.exception.PersistenceException;
import ro.cs.tao.services.base.WorkflowBuilderBase;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * A OptimizedWorkflowBuilder is a helper class for creating a workflow from a optimization graph.
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

    public WorkflowNodeDescriptor getOptimizedNode(OptimizationNode node) {
        WorkflowNodeDescriptor optimizedNode = null;

        if (node.getComponents().size() == 0) {
            /* error: an optimization node cannot exit without a component */

        } else if (node.getComponents().size() == 1) {
            /* one node in list */
            optimizedNode = getClone(node);

        } else {
            /* aggregate list */
            List<ProcessingComponent> components = new ArrayList<>();

            node.getComponents().forEach((id) ->
                                 components.add(node.getComponentDefinition().getComponent(id)));

            RuntimeOptimizer optimizer = Optimizers.findOptimizer(components.get(0).getContainerId());

            ProcessingComponent result = optimizer.aggregate(components.toArray(new ProcessingComponent[0]));

            optimizedNode = makeNodeWithComponent(result);
        }

        return optimizedNode;
    }

    private WorkflowNodeDescriptor getClone(OptimizationNode node) {
        return null;
        // TODO
    }

    private WorkflowNodeDescriptor makeNodeWithComponent(ProcessingComponent comp) {
        return null;
        // TODO
    }
}
