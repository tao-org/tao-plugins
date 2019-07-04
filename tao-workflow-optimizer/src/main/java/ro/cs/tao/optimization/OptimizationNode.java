package ro.cs.tao.optimization;

import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.RuntimeOptimizer;
import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.execution.Optimizers;
import ro.cs.tao.persistence.exception.PersistenceException;
import ro.cs.tao.services.utils.WorkflowUtilities;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * An OptimizationNode is used for simulating a WorkflowNodeDescriptor, but with dependencies in both ways.
 *
 * @author Alexandru Pirlea
 */
public class OptimizationNode {
    private List<OptimizationNode> adj       = new ArrayList<>();
    private List<OptimizationNode> adjParent = new ArrayList<>();
    private List<WorkflowNodeDescriptor> wNodes = new ArrayList<>();

    public OptimizationNode(WorkflowNodeDescriptor wNode) {
        wNodes.add(wNode);
    }

    public List<OptimizationNode> getAdj() {
        return adj;
    }

    public void setAdj(List<OptimizationNode> adj) {
        this.adj = adj;
    }

    public List<OptimizationNode> getAdjParent() {
        return adjParent;
    }

    public WorkflowNodeDescriptor getFirstwNode() {
        return wNodes.get(0);
    }

    public List<WorkflowNodeDescriptor> getwNodes() {
        return wNodes;
    }

    public void addChild(OptimizationNode n) {
        adj.add(n);
    }

    public void addParent(OptimizationNode n) {
        adjParent.add(n);
    }

    public boolean isCompatibleWith(OptimizationNode node) {
        if (node != null) {
            try {
                WorkflowNodeDescriptor u = this.getFirstwNode();
                WorkflowNodeDescriptor v = this.getFirstwNode();

                TaoComponent uComp = WorkflowUtilities.findComponent(u);
                TaoComponent vComp = WorkflowUtilities.findComponent(v);

                if (uComp instanceof ProcessingComponent && vComp instanceof ProcessingComponent) {
                    String uContainerId = ((ProcessingComponent) uComp).getContainerId();
                    String vContainerId = ((ProcessingComponent) vComp).getContainerId();

                    if (uContainerId != null && vContainerId != null) {
                        RuntimeOptimizer uOptimizer = Optimizers.findOptimizer(uContainerId);
                        RuntimeOptimizer vOptimizer = Optimizers.findOptimizer(vContainerId);

                        if (uOptimizer != null && vOptimizer != null) {
                            return uOptimizer.equals(vOptimizer);
                        }
                    }
                }
            } catch (PersistenceException e) {
                return false;
            }
        }

        return false;
    }
}
