package ro.cs.tao.sen2agri;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import ro.cs.tao.component.*;
import ro.cs.tao.component.enums.ParameterType;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.component.template.TemplateType;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.execution.ExecutionConfiguration;
import ro.cs.tao.persistence.*;
import ro.cs.tao.sen2agri.model.Graph;
import ro.cs.tao.sen2agri.model.Step;
import ro.cs.tao.sen2agri.model.Task;
import ro.cs.tao.services.base.WorkflowBuilderBase;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.services.bridge.spring.SpringContextBridgedServices;
import ro.cs.tao.services.utils.WorkflowUtilities;
import ro.cs.tao.topology.docker.BaseImageInstaller;
import ro.cs.tao.workflow.ExternalGraphConverter;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;
import ro.cs.tao.workflow.WorkflowNodeGroupDescriptor;
import ro.cs.tao.workflow.enums.ComponentType;
import ro.cs.tao.workflow.enums.TransitionBehavior;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

public class GraphConverter implements ExternalGraphConverter {
    private static final String WORKFLOW_NAME = "Sen2Agri-Workflow-%d";
    private static final String SOURCE_NAME = "generated_source";
    private static final String TARGET_NAME = "generated_target";

    private static final ContainerProvider containerProvider;
    private static final WorkflowNodeProvider workflowNodeProvider;
    private static final ProcessingComponentProvider componentProvider;
    private static final GroupComponentProvider groupProvider;
    private final Map<String, TaoComponent> componentMap;
    private final Map<String, Map<String, String>> values;
    private String dockerContainer;

    static {
        final SpringContextBridgedServices services = SpringContextBridge.services();
        containerProvider = services.getService(ContainerProvider.class);
        workflowNodeProvider = services.getService(WorkflowNodeProvider.class);
        componentProvider = services.getService(ProcessingComponentProvider.class);
        groupProvider = services.getService(GroupComponentProvider.class);
    }

    public GraphConverter() {
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
        this.values = new HashMap<>();
        this.componentMap = new HashMap<>();
    }

    @Override
    public WorkflowDescriptor convert(String jsonGraph, String container) throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final Graph graph = mapper.readerFor(Graph.class).readValue(jsonGraph);
        this.values.clear();
        this.dockerContainer = container;
        return new WorkflowBuilderBase() {
            @Override
            public String getName() { return String.format(WORKFLOW_NAME, graph.getJobId()); }

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
                final List<Task> tasks = graph.getTasks();
                //final Map<Integer, Task> mappedTasks = tasks.stream().collect(Collectors.toMap(Task::getId, Function.identity()));
                final Map<Integer, WorkflowNodeDescriptor> mappedNodes = new HashMap<>();
                tasks.sort(Comparator.comparingInt(Task::getId));
                Map<String, String> replacements = new HashMap<>();
                final String outputFolder = graph.getOutputFolder();
                final String temporaryFolder = graph.getTemporaryFolder();
                final String tempMount = ExecutionConfiguration.getDockerNodeTempMount();
                final boolean shouldMoveOrCopy = outputFolder != null && !outputFolder.equals(temporaryFolder);
                if (shouldMoveOrCopy && temporaryFolder != null) {
                    if (tempMount != null) {
                        replacements.put(temporaryFolder, tempMount.substring(tempMount.indexOf(":") + 1));
                    }
                }
                for (Task task : tasks) {
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
                    node.setPreserveOutput(graph.isKeepIntermediate());
                    workflowNodeProvider.update(node);
                }
                if (shouldMoveOrCopy) {
                    final Map<String, String> customValues = new HashMap<>();
                    customValues.put("source", replacements.get(temporaryFolder));
                    customValues.put("dest", outputFolder);
                    Container container = dockerContainer != null ?
                            containerProvider.getByName(dockerContainer) : containerProvider.getByName("sen2agri");
                    final List<WorkflowNodeDescriptor> terminals = WorkflowUtilities.findTerminals(workflow.getNodes());
                    if (terminals.size() > 0) {
                        addUtilityNode(workflow,
                                graph.isKeepIntermediate() ? "Copy" : "Move",
                                container.getId(),
                                customValues,
                                terminals.get(0), Direction.RIGHT);
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

    private WorkflowNodeDescriptor convert(WorkflowDescriptor parent, Task task, Map<String, String> replacements) throws PersistenceException {
        if (task == null) {
            throw new IllegalArgumentException("[task] null");
        }
        final List<Step> steps = task.getSteps();
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("[task.steps] null or empty");
        }
        List<WorkflowNodeDescriptor> nodes = new ArrayList<>();
        WorkflowNodeDescriptor node;
        for (Step step : steps) {
            ProcessingComponent component = convert(step, replacements);
            node = new WorkflowNodeDescriptor();
            node.setWorkflow(parent);
            node.setName(component.getLabel());
            node.setComponentId(component.getId());
            node.setBehavior(TransitionBehavior.FAIL_ON_ERROR);
            node.setComponentType(ComponentType.PROCESSING);
            node.setPreserveOutput(true);
            node.setCreated(LocalDateTime.now());
            final Map<String, String> values = this.values.get(component.getId());
            for (Map.Entry<String, String> entry : values.entrySet()) {
                node.addCustomValue(entry.getKey(), entry.getValue());
            }
            node.addInfo("externalTaskId", String.valueOf(task.getId()));
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

    private ProcessingComponent convert(Step step, Map<String, String> replacements) throws PersistenceException {
        if (step == null) {
            throw new IllegalArgumentException("[step] null");
        }
        final List<String> arguments = step.getArguments();
        if (arguments == null || arguments.isEmpty()) {
            throw new IllegalArgumentException("[step.arguments] null or empty");
        }
        final String name = step.getName();
        Container container = this.dockerContainer != null ?
                containerProvider.getByName(this.dockerContainer) : containerProvider.getByName("sen2agri");
        final boolean notExists = container == null;
        if (notExists) {
            container = new Container();
            container.setId(UUID.randomUUID().toString());
            container.setName("sen2agri");
            container.setDescription("Sen2Agri mock container");
            container.setTag("sen2agri");
            try {
                for (Application app : BaseImageInstaller.readCommonContainerDescriptor().getApplications()) {
                    if ("Copy".equals(app.getName()) || "Move".equals(app.getName()) || "Delete".equals(app.getName())) {
                        container.addApplication(app);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Application application = container.getApplications().stream().filter(a -> a.getName().equals(name)).findFirst().orElse(null);
        if (application == null) {
            // First create the container application
            application = new Application();
            application.setName(name);
            final Integer mem = step.getMem();
            if (mem != null && mem > 0) {
                application.setMemoryRequirements(mem);
            }
            application.setPath(arguments.get(0));
            container.addApplication(application);
            if (notExists) {
                containerProvider.save(container);
            } else {
                containerProvider.update(container);
            }
        }
        if (notExists) {
            try {
                final ProcessingComponent[] components = BaseImageInstaller.readCommonComponentsDescriptors();
                for (ProcessingComponent component : components) {
                    component.setId(UUID.randomUUID().toString());
                    component.getSources().forEach(s -> s.setParentId(component.getId()));
                    component.getTargets().forEach(t -> t.setParentId(component.getId()));
                    component.setContainerId(container.getId());
                    componentProvider.save(component);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ProcessingComponent component = componentProvider.get(name, container.getId());
        boolean collidedWithExisting = component != null && !component.getContainerId().equals(container.getId());
        if (collidedWithExisting) {
            component = null;
        }
        if (component == null) {
            // Next create the component
            final StringBuilder templateBuilder = new StringBuilder();
            component = new ProcessingComponent();
            component.setId(collidedWithExisting ? name + "-sen2agri" : name);
            component.setLabel(name);
            component.setDescription("Autogenerated component");
            component.setVisibility(ProcessingComponentVisibility.SYSTEM);
            component.setComponentType(ProcessingComponentType.EXTERNAL);
            component.setContainerId(container.getId());
            component.setVersion("1.0");
            component.setAuthors("TAO Team");
            component.setCopyright("TAO Team");
            component.setActive(true);
            final Integer procs = step.getProcs();
            if (procs != null && procs > 0) {
                component.setMultiThread(true);
                component.setParallelism(procs);
            }
            component.setFileLocation(application.getPath());
            component.setTemplateType(TemplateType.VELOCITY);
            this.values.put(component.getId(), new HashMap<>());
            ParameterDescriptor parameter = null;
            boolean prevMinus = false;
            for (int i = 1; i < arguments.size(); i++) {
                String arg = arguments.get(i);
                if (arg.startsWith("-")) {
                    if (!prevMinus) {
                        parameter = new ParameterDescriptor();
                        parameter.setDataType(String.class);
                        parameter.setId(StringUtils.stripStart(arg, "-"));
                        parameter.setDescription(parameter.getId());
                        parameter.setName(parameter.getId());
                        parameter.setLabel(parameter.getId());
                        parameter.setType(ParameterType.REGULAR);
                        templateBuilder.append(arg).append("\n");
                        prevMinus = true;
                    } else {
                        component.addParameter(parameter);
                        for (Map.Entry<String, String> entry : replacements.entrySet()) {
                            arg = arg.replace(entry.getKey(), entry.getValue());
                        }
                        this.values.get(component.getId()).put(parameter.getId(), arg);
                        templateBuilder.append("$").append(parameter.getId()).append("\n");
                        parameter = null;
                        prevMinus = false;
                    }
                } else {
                    if (parameter == null) {
                        // unnamed parameter
                        /*parameter = new ParameterDescriptor();
                        parameter.setDataType(String.class);
                        parameter.setId(arg);
                        parameter.setDescription(parameter.getId());
                        parameter.setName(parameter.getId());
                        parameter.setLabel(parameter.getId());
                        parameter.setType(ParameterType.REGULAR);*/
                        for (Map.Entry<String, String> entry : replacements.entrySet()) {
                            arg = arg.replace(entry.getKey(), entry.getValue());
                        }
                        templateBuilder.append(arg).append("\n");
                        prevMinus = false;
                        continue;
                    }
                    component.addParameter(parameter);
                    for (Map.Entry<String, String> entry : replacements.entrySet()) {
                        arg = arg.replace(entry.getKey(), entry.getValue());
                    }
                    this.values.get(component.getId()).put(parameter.getId(), arg);
                    templateBuilder.append("$").append(parameter.getId()).append("\n");
                    parameter = null;
                    prevMinus = false;
                }
            }
            component.setTemplateContents(templateBuilder.toString());
            component.addSource(createSource(component.getId()));
            component.addTarget(createTarget(component.getId()));
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
