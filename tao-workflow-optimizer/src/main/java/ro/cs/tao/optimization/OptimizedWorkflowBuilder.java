package ro.cs.tao.optimization;

import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.RuntimeOptimizer;
import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.execution.Optimizers;
import ro.cs.tao.persistence.exception.PersistenceException;
import ro.cs.tao.services.base.WorkflowBuilderBase;
import ro.cs.tao.services.utils.WorkflowUtilities;
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

    public WorkflowNodeDescriptor getOptimizedNode(List<WorkflowNodeDescriptor> wNodes) {
        WorkflowNodeDescriptor optimizedNode = null;

        if (wNodes.size() == 0) {
            /* error */

        } else if (wNodes.size() == 1) {
            /* one node in list */
            optimizedNode = getClone(wNodes.get(0));

        } else {
            /* aggregate list */
            List<ProcessingComponent> components = new ArrayList<>();

            try {
                for (WorkflowNodeDescriptor w : wNodes) {
                    components.add((ProcessingComponent) WorkflowUtilities.findComponent(w));
                }

            } catch (PersistenceException e) {
                components.clear();
                return null;
            }

            RuntimeOptimizer optimizer = Optimizers.findOptimizer(components.get(0).getContainerId());
            ProcessingComponent result = optimizer.aggregate(components.toArray(new ProcessingComponent[0]));
            optimizedNode = makeNodeWithComponent(result);
        }

        return optimizedNode;
    }

    private WorkflowNodeDescriptor getClone(WorkflowNodeDescriptor wNode) {
        return null;
        // TODO
    }

    private WorkflowNodeDescriptor makeNodeWithComponent(TaoComponent comp) {
        return null;
        // TODO
    }

    @Override
    protected void addLink(WorkflowDescriptor workflow, WorkflowNodeDescriptor parent, WorkflowNodeDescriptor child) throws PersistenceException {
        super.addLink(workflow, parent, child);
    }
}
