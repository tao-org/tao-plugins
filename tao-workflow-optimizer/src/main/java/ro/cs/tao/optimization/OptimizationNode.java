package ro.cs.tao.optimization;

import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.RuntimeOptimizer;
import ro.cs.tao.execution.Optimizers;

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
    private List<String> components = new ArrayList<>();
    private ComponentDefinition defined;

    public ComponentDefinition getComponentDefinition() {
        return defined;
    }

    public OptimizationNode(String componentId, ComponentDefinition defined) {
        components.add(componentId);
        this.defined = defined;
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

    public String getFirstComponent() {
        return components.get(0);
    }

    public List<String> getComponents() {
        return components;
    }

    public void addChild(OptimizationNode n) {
        adj.add(n);
    }

    public void addParent(OptimizationNode n) {
        adjParent.add(n);
    }

    public boolean isCompatibleWith(OptimizationNode node) {
        if (node != null) {
            ProcessingComponent uComp = defined.getComponent(this.getFirstComponent());
            ProcessingComponent vComp = defined.getComponent(node.getFirstComponent());

            String uContainerId = uComp.getContainerId();
            String vContainerId = vComp.getContainerId();

            /* optimizers must exist for both components and be the same */
            if (uContainerId != null && vContainerId != null) {
                RuntimeOptimizer uOptimizer = Optimizers.findOptimizer(uContainerId);
                RuntimeOptimizer vOptimizer = Optimizers.findOptimizer(vContainerId);

                if (uOptimizer != null && vOptimizer != null) {
                    return uOptimizer.equals(vOptimizer);
                }
            }
        }

        return false;
    }
}
