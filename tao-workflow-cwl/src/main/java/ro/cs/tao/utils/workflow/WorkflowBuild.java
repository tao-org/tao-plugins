package ro.cs.tao.utils.workflow;

import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.component.TargetDescriptor;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.eodata.enums.Visibility;
import ro.cs.tao.persistence.ContainerProvider;
import ro.cs.tao.persistence.PersistenceException;
import ro.cs.tao.persistence.WorkflowNodeProvider;
import ro.cs.tao.security.SessionStore;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.services.interfaces.ComponentService;
import ro.cs.tao.services.interfaces.WorkflowService;
import ro.cs.tao.utils.cwl.CWLApplicationTemplate;
import ro.cs.tao.utils.cwl.CWLUtils;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;
import ro.cs.tao.workflow.enums.ComponentType;
import ro.cs.tao.workflow.enums.Status;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

public class WorkflowBuild {
    private static final WorkflowService workflowService = SpringContextBridge.services().getService(WorkflowService.class);
    private static final WorkflowNodeProvider workflowNodeProvider = SpringContextBridge.services().getService(WorkflowNodeProvider.class);
    private static final ComponentService componentService = SpringContextBridge.services().getService(ComponentService.class);
    private static final ContainerProvider containerProvider = SpringContextBridge.services().getService(ContainerProvider.class);
    private static final float xOrigin = 300;
    private static final float yOrigin = 0;
    private static final float xStep = 300;
    private static final float yStep = 150;

    private static final Logger logger = Logger.getLogger(WorkflowBuild.class.getName());

    /**
     * Creates a workflow node descriptor from a TaoComponent.
     *
     * @param component the component used to create the workflow node
     * @param workflowDescriptor the workflow descriptor to which the node will belong
     * @return a workflow node descriptor
     */
    public static WorkflowNodeDescriptor createNodeFromComponent(TaoComponent component, String stepId, WorkflowDescriptor workflowDescriptor) throws PersistenceException {
        String name = stepId != null ? stepId : component.getLabel();
        return createNode(name, component.getId(), ComponentType.PROCESSING, workflowDescriptor, null, null);
    }

    /**
     * Creates and save into the database a workflow descriptor
     * @param workflowId the id of the workflow descriptor
     * @param alreadyExist whether a workflow with the same id already exists
     * @return a workflow descriptor
     */
    public static WorkflowDescriptor createDescriptor(String workflowId, boolean alreadyExist) {
        String workflowName = workflowId;
        if (alreadyExist) {
            workflowName = workflowId + "_" + UUID.randomUUID();
        }
        WorkflowDescriptor workflowDescriptor = new WorkflowDescriptor();
        workflowDescriptor.setName(workflowName);
        workflowDescriptor.setActive(true);
        workflowDescriptor.setVisibility(Visibility.PRIVATE);
        workflowDescriptor.setStatus(Status.DRAFT);
        workflowDescriptor.setCreated(LocalDateTime.now());
        workflowDescriptor.setUserId(SessionStore.currentContext().getPrincipal().getName());
        return workflowService.save(workflowDescriptor);
    }

    /**
     * Creates a workflow node descriptor and adds it to the database.
     *
     * @param name the workflow node name
     * @param id the component id
     * @param componentType the component type
     * @param workflowDescriptor the workflow descriptor to which this node will belong
     * @param relativeTo the node relative to which should be placed, it can be null
     * @param direction the direction in which to place the node, it can be null
     * @return the workflow node descriptor
     */
    public static WorkflowNodeDescriptor createNode(String name, String id, ComponentType componentType, WorkflowDescriptor workflowDescriptor, WorkflowNodeDescriptor relativeTo, Direction direction) throws PersistenceException {
        boolean existNodeId = workflowDescriptor.getNodes().stream().anyMatch(wfNode -> wfNode.getName().equals(name));
        WorkflowNodeDescriptor node = new WorkflowNodeDescriptor();
        node.setWorkflow(workflowDescriptor);
        if (existNodeId) {
            long numberOfOccurrences = workflowDescriptor.getNodes().stream().filter(wfNode -> wfNode.getName().matches(name + "_\\d+")).count() + 1;
            String generatedName = name.replaceAll("_\\d+", "");
            node.setName(generatedName + "_" + numberOfOccurrences);
        } else {
            node.setName(name);
        }
        node.setComponentId(id);
        node.setComponentType(componentType);
        node.setPreserveOutput(true);
        node.setCreated(LocalDateTime.now());
        node.setLevel(1); //default level on creation
        float[] coords = placeNode(relativeTo, direction);
        node.setxCoord(coords[0]);
        node.setyCoord(coords[1]);
        return workflowService.addNode(workflowDescriptor.getId(), node);
    }

    /**
     * Updates the docker container with the missing application templates.
     *
     * @param dockerContainer the docker container
     * @param applicationList the list of application template
     * @return the container
     */
    public static Container addMissingAppInContainer(Container dockerContainer, List<CWLApplicationTemplate> applicationList) throws PersistenceException {
        List<Application> containerApplications = dockerContainer.getApplications();
        for (CWLApplicationTemplate application : applicationList) {
            String appSimplifyName = CWLUtils.simplifyApplicationName(application.getApplicationName());
            String appPathNameSimplifyMerge = application.getApplicationPath() + "/" + application.getApplicationName();
            boolean missingApp = containerApplications.isEmpty() || containerApplications.stream()
                    .noneMatch(app -> app.getPath().equalsIgnoreCase(application.getApplicationName())
                            || app.getName().equals(appSimplifyName) || app.getPath().equalsIgnoreCase(appPathNameSimplifyMerge));
            if (missingApp) {
                Application tempApplication = new Application();
                tempApplication.setMemoryRequirements(4096);
                tempApplication.setName(CWLUtils.simplifyApplicationName(application.getApplicationName()));
                tempApplication.setPath(application.getApplicationName());//the path is the name of the application (as is found)
                dockerContainer.getApplications().add(tempApplication);
            }
        }
        return containerProvider.update(dockerContainer);
    }

    /**
     * Connects input/output of tow workflow nodes.
     *
     * @param parent the parent node from which the output will be connected
     * @param child the child node from which the input will be connected
     * @param sourceName the source name of the parent note
     * @param targetName the target name of the child node
     * @return the workflow node descriptor
     */
    public static WorkflowNodeDescriptor addLink(WorkflowNodeDescriptor parent, WorkflowNodeDescriptor child, String sourceName, String targetName) {
        if (parent != null && child != null) {
            ProcessingComponent sourceComponent = componentService.findById(parent.getComponentId());
            ProcessingComponent targetComponent = componentService.findById(child.getComponentId());

            Optional<TargetDescriptor> sourceTarget = sourceComponent.getTargets().stream().filter(s -> s.getName().equals(sourceName)).findFirst();
            Optional<SourceDescriptor> targetSource = targetComponent.getSources().stream().filter(s -> s.getName().equals(targetName)).findFirst();
            if (sourceTarget.isPresent() && targetSource.isPresent()) {
                try {
                    return workflowService.addLink(parent.getId(), sourceTarget.get().getId(),
                            child.getId(), targetSource.get().getId());
                } catch (PersistenceException e) {
                    throw new IllegalStateException("Cannot link " + parent.getName() + "/" + sourceName + " to " + child.getName() + "/" + targetName, e);
                }
            }
            throw new IllegalArgumentException("Missing port for CWL link: " + sourceName + " -> " + targetName);
        } else {
            throw new RuntimeException("Unable to add link");
        }
    }

    /**
     * Updates node name, in order to keep the node component id as name
     * @param workflowDescriptor the workflow node descriptor
     */
    public static void updateNodeNames(WorkflowDescriptor workflowDescriptor) throws PersistenceException {
        for (WorkflowNodeDescriptor node : workflowDescriptor.getNodes()) {
            String nodeName = CWLUtils.transformFromNameToCompId(node.getName(), node.getComponentId());
            String tempName = nodeName;
            List<WorkflowNodeDescriptor> existNodeList = workflowDescriptor.getNodes().stream()
                    .filter((wfNode) -> wfNode.getName().equals(tempName) || wfNode.getName().matches(tempName + "\\s?\\(\\d+\\)")).toList();
            if (existNodeList != null && !existNodeList.isEmpty()) {
                List<String> existNodeIdList = existNodeList.stream()
                        .filter(nodeDesc -> nodeDesc.getName().contains(" ("))
                        .map(nodeDesc -> nodeDesc.getName()
                                .replace(tempName, "").replaceAll("\\(|\\)|\\s", ""))
                        .toList();
                int maxIdNumber = 0;
                if (existNodeIdList != null && !existNodeIdList.isEmpty()) {
                    maxIdNumber = existNodeIdList.stream()
                            .mapToInt(Integer::parseInt)
                            .max().orElse(0);
                }
                nodeName = nodeName + " (" + (maxIdNumber + 1) + ")";
            }
            node.setName(nodeName);
            workflowDescriptor.getNodeById(node.getId()).setName(node.getName());
            workflowNodeProvider.update(node);
        }
    }

    public static WorkflowNodeDescriptor updatePosition(long workflowNodeId, WorkflowNodeDescriptor relativeTo, Direction direction) throws PersistenceException {
        float[] coords = placeNode(relativeTo, direction);
        workflowNodeProvider.updatePosition(workflowNodeId, coords);
        return workflowNodeProvider.get(workflowNodeId);
    }

    /**
     * Updates the canvas position for each workflow node found in a workflow descriptor, base on node level.
     *
     * @param workflowDescriptor the workflow node descriptor
     */
    public static void placeWorkflowNodes(WorkflowDescriptor workflowDescriptor) throws PersistenceException {
        WorkflowNodeDescriptor relativeTo;
        Direction direction;
        List<WorkflowNodeDescriptor> orderedNodes = workflowDescriptor.orderNodesByLevel();
        for (WorkflowNodeDescriptor node : orderedNodes) {
            List<WorkflowNodeDescriptor> sameLevelNodes = workflowDescriptor.findSameLevelNodes(workflowDescriptor.getNodes(), node.getLevel());
            if (node.getLevel() > 1) {
                List<WorkflowNodeDescriptor> parents = workflowDescriptor.findParents(workflowDescriptor.getNodes(), node);
                if (sameLevelNodes.size() > 1) {
                    List<WorkflowNodeDescriptor> parentsAndSiblings = new ArrayList<>();
                    parentsAndSiblings.addAll(parents);
                    parentsAndSiblings.addAll(sameLevelNodes);
                    WorkflowNodeDescriptor bottommostNode = getBottommost(sameLevelNodes);
                    WorkflowNodeDescriptor rightmostNode = getRightmost(parentsAndSiblings);
                    if (Objects.equals(bottommostNode.getId(), rightmostNode.getId())
                            || (bottommostNode.getxCoord() == rightmostNode.getxCoord() && bottommostNode.getLevel() == rightmostNode.getLevel())) {
                        relativeTo = bottommostNode;
                        direction = Direction.BOTTOM;
                    } else {
                        relativeTo = rightmostNode;
                        direction = Direction.BOTTOM_RIGHT;
                    }
                } else {
                    relativeTo = getRightmost(parents);
                    direction = Direction.RIGHT;
                }
            } else {
                relativeTo = getBottommost(sameLevelNodes);
                direction = Direction.BOTTOM;
            }
            WorkflowNodeDescriptor updatedNode = updatePosition(node.getId(), relativeTo, direction);
            workflowDescriptor.getNodeById(updatedNode.getId()).setxCoord(updatedNode.getxCoord());
            workflowDescriptor.getNodeById(updatedNode.getId()).setyCoord(updatedNode.getyCoord());
        }
    }

    public static WorkflowNodeDescriptor getRightmost(List<WorkflowNodeDescriptor> parents) {
        WorkflowNodeDescriptor foundElement = parents.getFirst();
        for (WorkflowNodeDescriptor ancestor : parents) {
            if (ancestor.getxCoord() > foundElement.getxCoord()) {
                foundElement = ancestor;
            }
        }
        return foundElement;
    }

    public static WorkflowNodeDescriptor getBottommost(List<WorkflowNodeDescriptor> parents) {
        WorkflowNodeDescriptor foundElement = parents.getFirst();
        for (WorkflowNodeDescriptor ancestor : parents) {
            if (ancestor.getyCoord() > foundElement.getyCoord()) {
                foundElement = ancestor;
            }
        }
        return foundElement;
    }

    private static float[] placeNode(WorkflowNodeDescriptor relativeTo, Direction direction) {
        float x;
        float y;
        if (relativeTo == null) {
            x = xOrigin;
            y = yOrigin;
        } else {
            x = relativeTo.getxCoord();
            y = relativeTo.getyCoord();
        }

        if (direction != null) {
            switch (direction) {
                case TOP:
                    y -= yStep;
                    break;
                case BOTTOM:
                    y += yStep;
                    break;
                case LEFT:
                    x -= xStep;
                    break;
                case RIGHT:
                    x += xStep;
                    break;
                case TOP_LEFT:
                    x -= xStep;
                    y -= yStep;
                    break;
                case TOP_RIGHT:
                    x += xStep;
                    y -= yStep;
                    break;
                case BOTTOM_LEFT:
                    x -= xStep;
                    y += yStep;
                    break;
                case BOTTOM_RIGHT:
                    x += xStep;
                    y += yStep;
                    break;
            }
        }
        return new float[] { x, y };
    }

    public enum Direction {
        TOP,
        LEFT,
        BOTTOM,
        RIGHT,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }
}
