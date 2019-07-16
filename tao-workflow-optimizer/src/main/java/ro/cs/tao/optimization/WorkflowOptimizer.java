package ro.cs.tao.optimization;

import ro.cs.tao.component.ComponentLink;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.TaoComponent;
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

    private static OptimizationGraph buildGraph(WorkflowDescriptor workflow, ComponentsDefinition defined) {
        OptimizationGraph graph = new OptimizationGraph();
        Map<Long, OptimizationNode> references = new HashMap<>();

        /* define an optimization node for every workflow node */
        for (WorkflowNodeDescriptor wNode : workflow.getNodes()) {

            /* create optimization node with the component from this node */
            OptimizationNode node = new OptimizationNode(wNode.getComponentId(), defined);

            /* add node to graph */
            graph.addNode(node);

            /* add reference from node id to workflow node */
            references.put(wNode.getId(), node);

            /* link componentId with it's workflow node */
            defined.addWorkflowNode(wNode, wNode.getComponentId());
            try {
                /* get component from mode */
                TaoComponent comp = WorkflowUtilities.findComponent(wNode);

                if (comp instanceof ProcessingComponent) {
                    /* link componentId with the component */
                    defined.addComponent((ProcessingComponent) comp, wNode.getComponentId());

                } else {
                    /* component is not processing component */
                    defined.addComponent(null, wNode.getComponentId());

                }
            } catch (PersistenceException e) {
                /* error: persistence exception */
                return null;

            }
        }

        /* set dependencies */
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
        boolean ok;

        do {
            ok = false;

            for (int i = 0; i < graph.size(); i++) {
                OptimizationNode u = graph.getNode(i);

                /* node u must have only one successor */
                if (u.getSuccessors().size() == 1) {
                    OptimizationNode v = u.getSuccessors().get(0);

                    /* node v must have only one predecessor */
                    if (v.getPredecessors().size() == 1) {

                        /* nodes must be compatible */
                        if (u.isCompatibleWith(v)) {
                            ok = true;

                            /* merge as group node */
                            u.getComponentIds().addAll(v.getComponentIds());

                            /* change graph neighbours */
                            u.setSuccessors(v.getSuccessors());

                            /* remove v from graph */
                            graph.removeNode(v);
                        }
                    }
                }
            }
        } while (ok);
    }

    private static WorkflowDescriptor createWorkflow(OptimizationGraph graph) {
        try {
            Map<OptimizationNode, WorkflowNodeDescriptor> translation = new HashMap<>();

            /* make empty workflow */
            OptimizedWorkflowBuilder builder = new OptimizedWorkflowBuilder();
            WorkflowDescriptor workflow = builder.createWorkflowDescriptor();

            /* for every optimization node make a workflow node */
            for (OptimizationNode node : graph.getNodes()) {
                WorkflowNodeDescriptor wNode = builder.createOptimizedNode(node, workflow);

                translation.put(node, wNode);
            }

            /* add links based on graph */
            for (Map.Entry<OptimizationNode, WorkflowNodeDescriptor> e : translation.entrySet()) {
                List<OptimizationNode> parents = e.getKey().getPredecessors();
                WorkflowNodeDescriptor child = e.getValue();

                for (OptimizationNode node : parents) {
                    WorkflowNodeDescriptor parent = translation.get(node);

                    builder.addLink(workflow, parent, child);
                }
            }

            return workflow;

        } catch (PersistenceException e) {
            /* error: persistence */
            return null;

        }
    }

    public static WorkflowDescriptor getOptimizedWorkflow(WorkflowDescriptor workflow) {
        ComponentsDefinition defined = new ComponentsDefinition();

        OptimizationGraph graph = buildGraph(workflow, defined);

        if (graph == null) {
            /* error while creating optimization graph */
            return null;
        }

        /* for every optimization node with more than one component there is an optimization chain */
        optimizeGraph(graph);

        return createWorkflow(graph);
    }
}
