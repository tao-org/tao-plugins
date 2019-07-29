package ro.cs.tao.optimization;

import ro.cs.tao.component.RuntimeOptimizer;

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
    private List<Long> nodeIds = new ArrayList<>();
    private ComponentsDefinition defined;
    private int level = -1;

    public ComponentsDefinition getComponentDefinition() {
        return defined;
    }

    public OptimizationNode(Long nodeId, ComponentsDefinition defined) {
        nodeIds.add(nodeId);
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

    public Long getFirstNodeId() {
        return nodeIds.get(0);
    }

    public List<Long> getNodeIds() {
        return nodeIds;
    }

    public void addChild(OptimizationNode n) {
        successors.add(n);
    }

    public void addParent(OptimizationNode n) {
        predecessors.add(n);
    }

    public boolean isCompatibleWith(OptimizationNode node) {
        if (node != null) {
            RuntimeOptimizer uOptimizer = defined.getOptimizer(this.getFirstNodeId());
            RuntimeOptimizer vOptimizer = defined.getOptimizer(node.getFirstNodeId());

            if (uOptimizer != null && vOptimizer != null) {
                return uOptimizer.equals(vOptimizer);
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
