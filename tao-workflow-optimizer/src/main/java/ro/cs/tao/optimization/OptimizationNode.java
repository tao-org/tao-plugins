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
    private List<OptimizationNode> successors   = new ArrayList<>();
    private List<OptimizationNode> predecessors = new ArrayList<>();
    private List<String> componentIds = new ArrayList<>();
    private ComponentsDefinition defined;
    private int level = -1;

    public ComponentsDefinition getComponentDefinition() {
        return defined;
    }

    public OptimizationNode(String componentId, ComponentsDefinition defined) {
        componentIds.add(componentId);
        this.defined = defined;
    }

    public List<OptimizationNode> getSuccessors() {
        return successors;
    }

    public void setSuccessors(List<OptimizationNode> successors) {
        this.successors = successors;
    }

    public List<OptimizationNode> getPredecessors() {
        return predecessors;
    }

    public String getFirstComponentId() {
        return componentIds.get(0);
    }

    public List<String> getComponentIds() {
        return componentIds;
    }

    public void addChild(OptimizationNode n) {
        successors.add(n);
    }

    public void addParent(OptimizationNode n) {
        predecessors.add(n);
    }

    public boolean isCompatibleWith(OptimizationNode node) {
        if (node != null) {
            ProcessingComponent uComp = defined.getComponent(this.getFirstComponentId());
            ProcessingComponent vComp = defined.getComponent(node.getFirstComponentId());

            /* comp is null if not processing */
            if (uComp != null && vComp != null) {
                String uContainerId = uComp.getContainerId();
                String vContainerId = vComp.getContainerId();

                /* optimizers must exist for both componentIds and be the same */
                if (uContainerId != null && vContainerId != null) {
                    RuntimeOptimizer uOptimizer = Optimizers.findOptimizer(uContainerId);
                    RuntimeOptimizer vOptimizer = Optimizers.findOptimizer(vContainerId);

                    if (uOptimizer != null && vOptimizer != null) {
                        return uOptimizer.equals(vOptimizer);
                    }
                }
            }
        }

        return false;
    }

    public int getLevel() {
        // if level is not defined
        if (level == -1) {
            if (predecessors.isEmpty()) {
                level = 0;

            } else {
                predecessors.forEach((n) -> level = Integer.max(level, n.getLevel()));
                level++;

            }
        }

        return level;
    }
}
