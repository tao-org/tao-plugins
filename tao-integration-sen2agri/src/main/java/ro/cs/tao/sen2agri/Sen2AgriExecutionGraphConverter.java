package ro.cs.tao.sen2agri;

import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.component.*;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.component.template.TemplateType;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.docker.DockerVolumeMap;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.persistence.*;
import ro.cs.tao.sen2agri.model.ExternalGraph;
import ro.cs.tao.sen2agri.model.ExternalStep;
import ro.cs.tao.sen2agri.model.ExternalTask;
import ro.cs.tao.services.base.WorkflowBuilderBase;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.services.bridge.spring.SpringContextBridgedServices;
import ro.cs.tao.services.utils.WorkflowUtilities;
import ro.cs.tao.topology.docker.DockerManager;
import ro.cs.tao.workflow.ExternalGraphConverter;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;
import ro.cs.tao.workflow.WorkflowNodeGroupDescriptor;
import ro.cs.tao.workflow.enums.ComponentType;
import ro.cs.tao.workflow.enums.TransitionBehavior;

import java.io.InvalidObjectException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;

public class Sen2AgriExecutionGraphConverter implements ExternalGraphConverter {
    private static final String WORKFLOW_NAME = "Sen2Agri-Workflow-%d";
    private static final String SOURCE_NAME = "generated_source";
    private static final String TARGET_NAME = "generated_target";

    private static final ContainerProvider containerProvider;
    private static final WorkflowNodeProvider workflowNodeProvider;
    private static final ProcessingComponentProvider componentProvider;
    private static final GroupComponentProvider groupProvider;
    private final Map<String, TaoComponent> componentMap;
    private final Logger logger;
    private Container dockerContainer;

    static {
        final SpringContextBridgedServices services = SpringContextBridge.services();
        containerProvider = services.getService(ContainerProvider.class);
        workflowNodeProvider = services.getService(WorkflowNodeProvider.class);
        componentProvider = services.getService(ProcessingComponentProvider.class);
        groupProvider = services.getService(GroupComponentProvider.class);
    }

    public Sen2AgriExecutionGraphConverter() {
        if (containerProvider == null) {
            throw new IllegalArgumentException("[containerProvider] null");
        }
        if (workflowNodeProvider == null) {
            throw new IllegalArgumentException("[workflowNodeProvider] null");
        }
        if (componentProvider == null) {
            throw new IllegalArgumentException("[componentProvider] null");
        }
        if (groupProvider == null) {
            throw new IllegalArgumentException("[groupProvider] null");
        }
        this.logger = Logger.getLogger(getClass().getName());
        this.componentMap = new HashMap<>();
    }

    @Override
    public WorkflowDescriptor convert(String jsonGraph, String container) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final ExternalGraph externalGraph = mapper.readerFor(ExternalGraph.class).readValue(jsonGraph);
        if ((this.dockerContainer = containerProvider.getByName(container)) == null) {
            if ((this.dockerContainer = DockerManager.getDockerImage(container)) == null) {
                throw new InvalidObjectException(String.format("The container %s is not registered", container));
            } else {
                logger.info(String.format("Container %s was not found in database, but is registered with the local Docker instance",
                                           container));
            }
        }
        return new WorkflowBuilderBase() {
            @Override
            public String getName() { return String.format(WORKFLOW_NAME, externalGraph.getJobId()); }

            @Override
            public WorkflowDescriptor createWorkflowDescriptor() throws PersistenceException {
                final WorkflowDescriptor workflowDescriptor = persistenceManager.workflows().getByName(getName());
                if (workflowDescriptor != null) {
                    persistenceManager.workflows().delete(workflowDescriptor);
                }
                final WorkflowDescriptor workflow = super.createWorkflowDescriptor();
                final List<WorkflowNodeDescriptor> orderedNodes = workflow.getOrderedNodes();
                for (WorkflowNodeDescriptor node : orderedNodes) {
                    node.setLevel(computeDistanceToRoot(node));
                }
                orderedNodes.sort(Comparator.comparingInt(WorkflowNodeDescriptor::getLevel));
                Map<Integer, List<Long>> nodesPerLevel = new HashMap<>();
                for (WorkflowNodeDescriptor node : orderedNodes) {
                    final int level = node.getLevel();
                    if (!nodesPerLevel.containsKey(level)) {
                        nodesPerLevel.put(level, new ArrayList<>());
                    }
                    nodesPerLevel.get(level).add(node.getId());
                }
                for (WorkflowNodeDescriptor node : orderedNodes) {
                    List<Long> siblings = nodesPerLevel.get(node.getLevel());
                    int idx = 0;
                    for (int i = 0; i < siblings.size(); i++) {
                        if (siblings.get(i).equals(node.getId())) {
                            idx = i;
                            break;
                        }
                    }
                    updateNodePosition(node, idx);
                }
                return workflow;
            }

            @Override
            protected void addNodes(WorkflowDescriptor workflow) throws PersistenceException {
                final List<ExternalTask> tasks = externalGraph.getTasks();
                //final Map<Integer, Task> mappedTasks = tasks.stream().collect(Collectors.toMap(Task::getId, Function.identity()));
                final Map<Integer, WorkflowNodeDescriptor> mappedNodes = new HashMap<>();
                tasks.sort(Comparator.comparingInt(ExternalTask::getId));
                Map<String, String> replacements = new HashMap<>();
                final String outputFolder = externalGraph.getOutputFolder();
                final String temporaryFolder = externalGraph.getTemporaryFolder();
                final String configFolder = externalGraph.getConfigurationFolder();
                final DockerVolumeMap volumeMap = ExecutionConfiguration.getWorkerContainerVolumeMap();
                final boolean shouldMoveOrCopy = outputFolder != null && !outputFolder.equals(temporaryFolder);
                replacements.put(configFolder, volumeMap.getContainerConfigurationFolder());
                if (shouldMoveOrCopy && temporaryFolder != null) {
                    replacements.put(temporaryFolder, volumeMap.getContainerTemporaryFolder());
                }
                for (ExternalTask task : tasks) {
                    final WorkflowNodeDescriptor node = convert(workflow, task, replacements);
                    mappedNodes.put(task.getId(), node);
                    workflow.addNode(node);
                    final List<Integer> precedingTasksIds = task.getPrecedingTasksIds();
                    if (precedingTasksIds != null && precedingTasksIds.size() > 0) {
                        final int count = precedingTasksIds.size();
                        Direction[][] directions = new Direction[count][];
                        for (int i = 1; i <= count; i++) {
                            if (i == count || (count % 2 == 0 && i == count / 2)) {
                                directions[i - 1] = new Direction[] { Direction.RIGHT };
                            } else if (i <= count / 2) {
                                Direction[] d = new Direction[count / 2 - i + 1];
                                d[0] = Direction.TOP_RIGHT;
                                Arrays.fill(d, 1, d.length, Direction.TOP);
                                directions[i - 1] = d;
                            } else {
                                Direction[] d = new Direction[i - count / 2];
                                d[0] = Direction.BOTTOM_RIGHT;
                                Arrays.fill(d, 1, d.length, Direction.BOTTOM);
                                directions[i - 1] = d;
                            }
                        }
                        for (int i = 0; i < count; i++) {
                            final WorkflowNodeDescriptor parentNode = mappedNodes.get(precedingTasksIds.get(i));
                            if (parentNode != null) {
                                addLink(parentNode, TARGET_NAME, node, SOURCE_NAME);
                                placeNode(parentNode, directions[i]);
                            }
                        }
                    } else {
                        final float[] coords = placeNode(null);
                        node.setxCoord(coords[0]);
                        node.setyCoord(coords[1]);
                    }
                    node.setPreserveOutput(externalGraph.isKeepIntermediate());
                    workflowNodeProvider.update(node);
                }
                if (shouldMoveOrCopy) {
                    final Map<String, String> customValues = new HashMap<>();
                    customValues.put("source", replacements.get(temporaryFolder));
                    customValues.put("dest", outputFolder);
                    final List<WorkflowNodeDescriptor> terminals = WorkflowUtilities.findTerminals(workflow.getNodes());
                    if (terminals.size() > 0) {
                        final WorkflowNodeDescriptor utilityNode = addUtilityNode(workflow,
                                                                                  externalGraph.isKeepIntermediate() ? "Copy" : "Move",
                                                                                  dockerContainer.getId(),
                                                                                  customValues,
                                                                                  terminals.get(0), Direction.RIGHT);
                        for (int i = 1; i < terminals.size(); i++) {
                            final WorkflowNodeDescriptor leaf = terminals.get(i);
                            if (!utilityNode.equals(leaf)) {
                                final ProcessingComponent component = componentProvider.get(utilityNode.getComponentId(), dockerContainer.getId());
                                addLink(leaf, TARGET_NAME, utilityNode, component.getSources().get(0).getName());
                            }
                        }
                    }
                }
            }

            private void updateNodePosition(WorkflowNodeDescriptor node, int levelRank) throws PersistenceException {
                final int level = node.getLevel();
                float[] coords;
                Direction[] directions = null;
                if (level > 0) {
                    directions = new Direction[level];
                    Arrays.fill(directions, Direction.RIGHT);
                    if (levelRank > 0) {
                        directions = Arrays.copyOf(directions, directions.length + levelRank);
                    }
                    for (int i = 0; i < levelRank; i++) {
                        directions[level + i] = Direction.BOTTOM;
                    }
                }
                coords = placeNode(null, directions);
                node.setxCoord(coords[0]);
                node.setyCoord(coords[1]);
                workflowNodeProvider.update(node);
            }

            private int computeDistanceToRoot(WorkflowNodeDescriptor node) {
                final List<WorkflowNodeDescriptor> parents = WorkflowUtilities.findAncestors(node);
                int distance = parents != null && parents.size() > 0 ? 1 : 0;
                if (parents != null) {
                    for (WorkflowNodeDescriptor parent : parents) {
                        distance = Math.max(distance, computeDistanceToRoot(parent) + 1);
                    }
                }
                return distance;
            }

        }.createWorkflowDescriptor();
    }

    private WorkflowNodeDescriptor convert(WorkflowDescriptor parent, ExternalTask task, Map<String, String> replacements) throws PersistenceException {
        if (task == null) {
            throw new IllegalArgumentException("[task] null");
        }
        final List<ExternalStep> externalSteps = task.getSteps();
        if (externalSteps == null || externalSteps.isEmpty()) {
            throw new IllegalArgumentException("[task.steps] null or empty");
        }
        List<WorkflowNodeDescriptor> nodes = new ArrayList<>();
        WorkflowNodeDescriptor node;
        for (ExternalStep externalStep : externalSteps) {
            ProcessingComponent component = convert(externalStep, replacements);
            node = new WorkflowNodeDescriptor();
            node.setWorkflow(parent);
            node.setName(component.getLabel());
            node.setComponentId(component.getId());
            node.setBehavior(TransitionBehavior.FAIL_ON_ERROR);
            node.setComponentType(ComponentType.PROCESSING);
            node.setPreserveOutput(true);
            node.setCreated(LocalDateTime.now());
            node.addInfo("externalTaskId", String.valueOf(task.getId()));
            node.addInfo("externalStep", externalStep.getName());
            node = workflowNodeProvider.save(node);
            nodes.add(node);
        }
        if (nodes.size() == 1) {
            node = nodes.get(0);
        } else {
            node = new WorkflowNodeGroupDescriptor();
            node.setWorkflow(parent);
            GroupComponent component = GroupComponent.create(componentMap.get(nodes.get(0).getComponentId()).getSources(),
                                                             componentMap.get(nodes.get(nodes.size() - 1).getComponentId()).getTargets());
            component = groupProvider.save(component);
            componentMap.put(component.getId(), component);
            node.setComponentId(component.getId());
            node.setComponentType(ComponentType.GROUP);
            node.addInfo("externalTaskId", String.valueOf(task.getId()));
            node = workflowNodeProvider.save(node);
            WorkflowNodeGroupDescriptor grpNode = (WorkflowNodeGroupDescriptor) node;
            WorkflowNodeDescriptor previous;
            WorkflowNodeDescriptor current;
            for (int i = 0; i < nodes.size(); i++) {
                current = nodes.get(i);
                if (i > 0) {
                    previous = nodes.get(i - 1);
                    final List<TargetDescriptor> targets = componentMap.get(previous.getComponentId()).getTargets();
                    final List<SourceDescriptor> sources = componentMap.get(current.getComponentId()).getSources();
                    if (targets != null && !targets.isEmpty() && sources != null && !sources.isEmpty()) {
                        ComponentLink link = new ComponentLink();
                        link.setSourceNodeId(previous.getId());
                        link.setInput(targets.get(0));
                        link.setOutput(sources.get(0));
                        current.addLink(link);
                        workflowNodeProvider.save(current);
                    }
                }
                grpNode.addNode(current);
            }
        }
        node.setName(task.getName());
        return workflowNodeProvider.update(node);
    }

    private ProcessingComponent convert(ExternalStep externalStep, Map<String, String> replacements) throws PersistenceException {
        if (externalStep == null) {
            throw new IllegalArgumentException("[step] null");
        }
        final List<String> arguments = externalStep.getArguments();
        if (arguments == null || arguments.isEmpty()) {
            throw new IllegalArgumentException("[step.arguments] null or empty");
        }
        final String name = externalStep.getName();
        final boolean notExists = containerProvider.getByName(this.dockerContainer.getName()) == null;
        Container container = this.dockerContainer;
        if (notExists) {
            container = new Container();
            container.setId(this.dockerContainer.getId());
            container.setName(this.dockerContainer.getName());
            container.setDescription(this.dockerContainer.getDescription() == null ? "External container" : this.dockerContainer.getDescription());
            container.setTag(this.dockerContainer.getTag() != null ? this.dockerContainer.getTag() : "external");
        }
        Application application = container.getApplications().stream().filter(a -> a.getName().equals(name)).findFirst().orElse(null);
        if (application == null) {
            // First create the container application
            application = new Application();
            application.setName(name);
            final Integer mem = externalStep.getMem();
            if (mem != null && mem > 0) {
                application.setMemoryRequirements(mem);
            }
            application.setPath(arguments.get(0));
            container.addApplication(application);
            if (notExists) {
                this.dockerContainer = containerProvider.save(container);
            } else {
                this.dockerContainer = containerProvider.update(container);
            }
        }
        ProcessingComponent component = componentProvider.get(name, container.getId());
        boolean collidedWithExisting = component != null && !component.getContainerId().equals(container.getId());
        if (collidedWithExisting) {
            component = null;
        }
        if (component == null) {
            // Next create the component
            component = new ProcessingComponent();
            component.setId(collidedWithExisting ? name + "-sen2agri" : name);
            logger.finest(String.format("Creating component [%s]", component.getId()));
            component.setLabel(name);
            component.setDescription("Autogenerated component");
            component.setVisibility(ProcessingComponentVisibility.SYSTEM);
            component.setComponentType(ProcessingComponentType.EXTERNAL);
            component.setContainerId(container.getId());
            component.setVersion("1.0");
            component.setAuthors("TAO Team");
            component.setCopyright("TAO Team");
            component.setActive(true);
            final Integer procs = externalStep.getProcs();
            if (procs != null && procs > 0) {
                component.setMultiThread(true);
                component.setParallelism(procs);
            }
            component.setFileLocation(application.getPath());
            component.setTemplateType(TemplateType.VELOCITY);
            component.setTransient(true);
            final StringBuilder templateBuilder = new StringBuilder();
            templateBuilder.append("The following replacements will be made: \n");
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                templateBuilder.append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
            }
            logger.fine(templateBuilder.toString());
            templateBuilder.setLength(0);
            String arg;
            for (int i = 1; i < arguments.size(); i++) {
                arg = arguments.get(i);
                for (Map.Entry<String, String> entry : replacements.entrySet()) {
                    arg = arg.replace(Matcher.quoteReplacement(entry.getKey()), entry.getValue());
                }
                templateBuilder.append(arg).append("\n");
            }
            if (templateBuilder.length() > 1) {
                templateBuilder.setLength(templateBuilder.length() - 1);
            }
            component.setTemplateContents(templateBuilder.toString());
            component.addSource(createSource(component.getId()));
            component.addTarget(createTarget(component.getId()));
            if (externalStep.getOutput() != null) {
                final TargetDescriptor outTarget = createTarget(component.getId());
                outTarget.getDataDescriptor().setFormatType(DataFormat.RASTER);
                outTarget.getDataDescriptor().setLocation(externalStep.getOutput());
                component.addTarget(outTarget);
            }
            componentProvider.save(component);
        }
        componentMap.put(component.getId(), component);
        return component;
    }

    private TargetDescriptor createTarget(String parentId) {
        TargetDescriptor descriptor = new TargetDescriptor(UUID.randomUUID().toString());
        descriptor.setParentId(parentId);
        descriptor.setName(TARGET_NAME);
        descriptor.setCardinality(0);
        DataDescriptor dataDescriptor = new DataDescriptor();
        dataDescriptor.setFormatType(DataFormat.OTHER);
        descriptor.setDataDescriptor(dataDescriptor);
        return descriptor;
    }

    private SourceDescriptor createSource(String parentId) {
        SourceDescriptor descriptor = new SourceDescriptor(UUID.randomUUID().toString());
        descriptor.setParentId(parentId);
        descriptor.setName(SOURCE_NAME);
        descriptor.setCardinality(0);
        DataDescriptor dataDescriptor = new DataDescriptor();
        dataDescriptor.setFormatType(DataFormat.OTHER);
        descriptor.setDataDescriptor(dataDescriptor);
        return descriptor;
    }
}
