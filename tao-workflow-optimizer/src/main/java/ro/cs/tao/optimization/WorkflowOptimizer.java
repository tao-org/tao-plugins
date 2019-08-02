package ro.cs.tao.optimization;

import ro.cs.tao.component.ComponentLink;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.RuntimeOptimizer;
import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.execution.Optimizers;
import ro.cs.tao.persistence.exception.PersistenceException;
import ro.cs.tao.services.utils.WorkflowUtilities;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A WorkflowOptimizer is a helper class that tries to find a chain in workflow that can be executed
 * on the machine for bypassing the I/O.
 *
 * @author Alexandru Pirlea
 */
public class WorkflowOptimizer {

    private static OptimizationGraph createGraph(WorkflowDescriptor workflow, ComponentsDefinition defined) {
        OptimizationGraph graph = new OptimizationGraph();
        Map<Long, OptimizationNode> references = new HashMap<>();

        /* define an optimization node for every workflow node */
        for (WorkflowNodeDescriptor wNode : workflow.getNodes()) {

            /* create optimization node with the component from this node */
            OptimizationNode node = new OptimizationNode(wNode.getId(), defined);

            /* add node to graph */
            graph.addNode(node);

            /* add reference from node id to workflow node */
            references.put(wNode.getId(), node);

            /* link node id with it's workflow node */
            defined.addWorkflowNode(wNode, wNode.getId());

            try {
                /* get component from mode */
                TaoComponent comp = WorkflowUtilities.findComponent(wNode);

                if (comp instanceof ProcessingComponent) {
                    /* link componentId with the node id */
                    defined.addComponent((ProcessingComponent) comp, wNode.getId());

                    /* link RuntimeOptimizer with node id */
                    String containerId = ((ProcessingComponent) comp).getContainerId();
                    RuntimeOptimizer optimizer = Optimizers.findOptimizer(containerId);
                    defined.addOptimizer(optimizer, wNode.getId());

                } else {
                    /* component is not processing component */
                    defined.addComponent(null, wNode.getId());
                    defined.addOptimizer(null, wNode.getId());

                }
            } catch (PersistenceException e) {
                /* error: persistence exception */
                return null;

            }
        }

        /* set dependencies (u -> v) */
        for (WorkflowNodeDescriptor wNode : workflow.getNodes()) {
            Long vId = wNode.getId();
            OptimizationNode v = references.get(vId);

            for (ComponentLink link: wNode.getIncomingLinks()) {
                Long uId = link.getSourceNodeId();

                OptimizationNode u = references.get(uId);

                if (u == null) {
                    /* no known optimization node has node with id uId */
                    return null;
                }

                /* add link between optimization nodes both ways */
                u.addChild(v);
                v.addParent(u);
            }
        }

        return graph;
    }

    private static void optimizeGraph(OptimizationGraph graph) {
        boolean isModified;

        do {
            isModified = false;

            for (int i = 0; i < graph.size(); i++) {
                OptimizationNode u = graph.getNodes().get(i);

                OptimizationNode v = u.getSuccessors().stream().findFirst().orElse(null);

                /* nodes must be compatible for grouping */
                if (u.isCompatibleWith(v) && v != null) {
                    isModified = true;

                    /* merge as group node */
                    u.getNodeIds().addAll(v.getNodeIds());

                    /* change graph neighbours */
                    u.setSuccessors(v.getSuccessors());

                    /* remove v from graph */
                    graph.removeNode(v);
                }
            }
        } while (isModified);
    }

    private static WorkflowDescriptor createWorkflow(OptimizationGraph graph) {
        OptimizedWorkflowBuilder builder = new OptimizedWorkflowBuilder();

        try {
            Map<OptimizationNode, WorkflowNodeDescriptor> translation = new HashMap<>();

            /* make empty workflow */
            WorkflowDescriptor workflow = builder.createWorkflowDescriptor();

            /* for every optimization node make a workflow node */
            for (OptimizationNode node : graph.getNodes()) {
                WorkflowNodeDescriptor wNode = builder.createOptimizedNode(node, workflow);

                translation.put(node, wNode);
            }

            workflow = builder.updateWorkflowDescriptor(workflow);

            /* add links based on graph */
            for (Map.Entry<OptimizationNode, WorkflowNodeDescriptor> e : translation.entrySet()) {
                List<OptimizationNode> parents = e.getKey().getPredecessors();
                WorkflowNodeDescriptor child = e.getValue();

                for (OptimizationNode node : parents) {
                    WorkflowNodeDescriptor parent = translation.get(node);

                    builder.addLink(workflow, parent, child);
                }
            }

            return builder.updateWorkflowDescriptor(workflow);

        } catch (Exception e) {
            builder.cleanCache();
            return null;
        }
    }

    public static WorkflowDescriptor getOptimizedWorkflow(WorkflowDescriptor workflow) {
        if (workflow == null) {
            return null;
        }

        if (workflow.getNodes().isEmpty()) {
            return null;
        }

        ComponentsDefinition defined = new ComponentsDefinition();

        OptimizationGraph graph = createGraph(workflow, defined);

        if (graph == null) {
            /* error while creating optimization graph */
            return null;
        }

        /* for every optimization node with more than one component there is an optimization chain */
        optimizeGraph(graph);

        return createWorkflow(graph);
    }
}
