package ro.cs.tao.utils.executors;


import com.gs.collections.impl.utility.ListIterate;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.VolumeMount;
import ro.cs.tao.argo.workflow.model.*;
import ro.cs.tao.component.*;
import ro.cs.tao.datasource.DataSourceComponent;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.persistence.ExecutionTaskProvider;
import ro.cs.tao.execution.util.TaskUtilities;
import ro.cs.tao.persistence.WorkflowProvider;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.services.utils.WorkflowUtilities;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.container.ContainerUnit;
import ro.cs.tao.utils.initializers.ArgoWorkflowInitializer;
import ro.cs.tao.utils.yaml.ArgoYAMLWriter;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;
import ro.cs.tao.workflow.enums.ComponentType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static ro.cs.tao.utils.constants.ArgoConstants.*;

public class ArgoExecutor extends Executor<Object>
        implements ExecutionDescriptorConverter {

    private ExecutionUnit executionUnit;
    private final ExecutionTaskProvider taskProvider;
    private final WorkflowProvider workflowProvider;
    private final Set<WorkflowNodeDescriptor> visitedNodes;
    private final ArgoWorkflow argoWorkflow;

    public ArgoExecutor() {
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.visitedNodes = new HashSet<>();
        this.argoWorkflow = new ArgoWorkflow();
    }

    public ArgoExecutor(ExecutionUnit unit) {
        super(unit.getHost(), unit.getArguments(), unit.asSuperUser());
        this.executionUnit = unit;
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.visitedNodes = new HashSet<>();
        this.argoWorkflow = new ArgoWorkflow();
    }

    @Override
    public boolean isIntendedFor(ExecutionUnitFormat unitFormat) {
        return unitFormat == ExecutionUnitFormat.ARGO;
    }

    @Override
    public boolean canConnect() {
        return true;
    }

    @Override
    public int execute(boolean logMessages) {
        checkExecutionUnit();
        checkScriptPath();
        try{
            final Path targetPath = Paths.get(executionUnit.getScriptTargetPath());
            WorkflowDescriptor workflowDescriptor = getWorkflowDescriptor();
            initializeWorkflow(targetPath);
            parseNodes(workflowDescriptor);
            updateContainerInfo();
            synchronized (this.workflowProvider) {
                if (Files.exists(targetPath)) {
                    ArgoYAMLWriter.updateYaml(argoWorkflow, executionUnit, getExecutionUnitName());
                    logger.fine("Update Argo Graph File");
                    return 0;
                }
                ArgoYAMLWriter.createYaml(argoWorkflow, executionUnit);
                logger.fine("Create Argo Graph File");
            }
        } catch (Exception e){
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
        return 0;
    }

    private void initializeWorkflow(Path path) {
        ArgoWorkflowInitializer initializer = new ArgoWorkflowInitializer(argoWorkflow, path, executionUnit);
        initializer.initializeArgoWorkflow();
    }

    private void parseNodes(WorkflowDescriptor workflowDescriptor){
        List<WorkflowNodeDescriptor> roots = workflowDescriptor.findRoots(workflowDescriptor.getNodes());
        parseRootNodes(roots);
        for (WorkflowNodeDescriptor node  : roots) {
            parseNode(node);
        }
    }

    private void parseRootNodes(List<WorkflowNodeDescriptor> roots) {
        for(WorkflowNodeDescriptor node : roots){
            setupWorkflowArtifactsFromRoot(node);
        }
    }
    private void setupWorkflowArtifactsFromRoot(WorkflowNodeDescriptor node) {
        TaoComponent nodeComponent = TaskUtilities.getComponentFor(node);
        for (SourceDescriptor source : nodeComponent.getSources()) {
            if (DataSourceComponent.QUERY_PARAMETER.equals(source.getName())) {
                String inputId = nodeComponent.getTargets().getFirst().getName() + CONNECTOR_STRING + node.getId();
                String artifactName = MAIN_WF_INPUT_PREFIX + inputId;
                computeSpecArtifacts(source, artifactName);
            }
        }
    }

    private void computeSpecArtifacts(SourceDescriptor source, String artifactName){
        WorkflowArguments specArguments = argoWorkflow.getSpec().getArguments();
        List<WorkflowArtifact> workflowArtifacts = specArguments.getArtifacts() == null ? new ArrayList<>() : specArguments.getArtifacts();
        if (source.getDataDescriptor().getLocation().contains(",")) {
            String[] strings = source.getDataDescriptor().getLocation().split(",");
            for (int i=0; i < strings.length; i++) {
                WorkflowArtifact workflowArtifact = new WorkflowArtifact();
                workflowArtifact.setName(replaceWithConnectorString(artifactName) + CONNECTOR_STRING + i);
                workflowArtifact.setPath(FileUtilities.toUnixPath(strings[i]));
                workflowArtifacts.add(workflowArtifact);
            }
        } else {
            WorkflowArtifact workflowArtifact = new WorkflowArtifact();
            workflowArtifact.setName(replaceWithConnectorString(artifactName));
            workflowArtifact.setPath(FileUtilities.toUnixPath(source.getDataDescriptor().getLocation()));
            workflowArtifacts.add(workflowArtifact);
        }
        specArguments.setArtifacts(workflowArtifacts);
    }

    private void parseNode(WorkflowNodeDescriptor node) {
        List<WorkflowNodeDescriptor> children = getChildrenNodesToParse(node);
        setupArgoTemplateFromNode(node);
        visitedNodes.add(node);
        for(WorkflowNodeDescriptor nextNode : children){
            if(hasParentsVisited(nextNode) && !visitedNodes.contains(nextNode)) {
                parseNode(nextNode);
            }
        }
    }

    private List<WorkflowNodeDescriptor> getChildrenNodesToParse(WorkflowNodeDescriptor node) {
        WorkflowDescriptor workflowDescriptor = getWorkflowDescriptor();
        List<WorkflowNodeDescriptor> children = workflowDescriptor.findChildren(workflowDescriptor.getNodes(), node);
        List<WorkflowNodeDescriptor> orderedNodes = orderWorkflowNodes(workflowDescriptor);
        orderedNodes.retainAll(children);
        orderedNodes.removeIf(visitedNodes::contains);
        return orderedNodes;
    }

    private boolean hasParentsVisited(WorkflowNodeDescriptor nextNode) {
        List<WorkflowNodeDescriptor> parents = WorkflowUtilities.findAncestors(nextNode);
        parents.removeIf(p -> p.getComponentType().equals((ComponentType.DATASOURCE)));
        parents.removeAll(visitedNodes);
        return parents.isEmpty();
    }

    private void setupArgoTemplateFromNode(WorkflowNodeDescriptor node){
        if(node.getComponentType().equals(ComponentType.DATASOURCE))
            return;
        List<WorkflowTemplate> templates = argoWorkflow.getSpec().getTemplates();
        List<DAGTask> dagTasks = templates.getFirst().getDAG().getTasks();
        DAGTask dagTask = createNewDAGTask(node);
        dagTasks.add(dagTask);
        WorkflowTemplate workflowTemplate = createNewWorkflowTemplate(node);
        templates.add(workflowTemplate);
        computeTemplateDependencies(node, dagTask.getDependencies());
    }


    private DAGTask createNewDAGTask(WorkflowNodeDescriptor currentNode) {
        DAGTask dagTask = new DAGTask();
        initializeDAGTask(dagTask);
        String dagTaskName = replaceWithConnectorString(currentNode.getComponentId().toLowerCase());
        dagTask.setName(dagTaskName + CONNECTOR_STRING + currentNode.getId());
        dagTask.setTemplate(dagTaskName + CONNECTOR_STRING + currentNode.getId());
        return dagTask;
    }

    private void initializeDAGTask(DAGTask dagTask) {
        dagTask.setDependencies(new ArrayList<>());
    }

    private WorkflowTemplate createNewWorkflowTemplate(WorkflowNodeDescriptor currentNode) {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setName(replaceWithConnectorString(currentNode.getComponentId().toLowerCase()) + CONNECTOR_STRING + currentNode.getId());
        return workflowTemplate;
    }

    private void computeTemplateDependencies(WorkflowNodeDescriptor currentNode, List<String> dependencies){
        List<WorkflowNodeDescriptor> nodesExceptDataSources = getNodesExceptDataSources();
        for(ComponentLink link : currentNode.getIncomingLinks()){
            String dependencyId = link.getInput().getParentId().toLowerCase() + CONNECTOR_STRING + link.getSourceNodeId();
            for(WorkflowNodeDescriptor node : nodesExceptDataSources){
                String dependencyTemplateName = node.getComponentId().toLowerCase() + CONNECTOR_STRING + node.getId();
                if(dependencyId.equals(dependencyTemplateName)){
                    dependencies.add(replaceWithConnectorString(dependencyId));
                }
            }
        }
    }

    private List<WorkflowNodeDescriptor> getNodesExceptDataSources() {
        WorkflowDescriptor workflowDescriptor = getWorkflowDescriptor();
        List<WorkflowNodeDescriptor> nodesExceptDataSources = workflowDescriptor.getNodes();
        List<WorkflowNodeDescriptor> roots = workflowDescriptor.findRoots(workflowDescriptor.getNodes());
        List<WorkflowNodeDescriptor> dataSourceRootNodes =
                roots.stream()
                        .filter(r -> r.getComponentType().equals(ComponentType.DATASOURCE))
                        .toList();
        nodesExceptDataSources.removeAll(dataSourceRootNodes);
        return nodesExceptDataSources;
    }

    private void updateContainerInfo() {
        checkContainerUnit();
        WorkflowTemplate currentTemplate = getTemplateToBeUpdatedWithContainerInfo();
        Container container = new Container();
        currentTemplate.setContainer(container);
        List<String> bashArguments = getBashArguments();
        container.setArgs(getAllArgumentsForContainer(bashArguments));
        container.setEnv(getEnvVarForContainer());
        container.setCommand(getCommandsForContainer(bashArguments));
        container.setImage(getImageForContainer());
        container.setVolumeMounts(getVolumeMounts());
    }

    private List<VolumeMount> getVolumeMounts() {
        List<VolumeMount> volumeMounts = new ArrayList<>();
        for(Map.Entry<String, String> entry : executionUnit.getVolumeMap().entrySet()){
            VolumeMount volumeMount = new VolumeMount();
            volumeMount.setName(getVolumeName(entry.getKey()));
            volumeMount.setMountPath(entry.getValue());
            volumeMounts.add(volumeMount);
        }
        return volumeMounts;
    }

    private String getVolumeName(String path) {
        String[] partsOfPath = path.split("/");
        for(int i = partsOfPath.length -1 ; i > -1; i--){
            if(partsOfPath[i].matches(".*[a-zA-Z]+"))
                return partsOfPath[i];
        }
        return path;
    }

    private List<EnvVar> getEnvVarForContainer() {
        List<EnvVar> envVars = new ArrayList<>();
        envVars.addAll(getEnvVarsFromExecutionUnit());
        envVars.addAll(getEnvVarsFromVariables());
        return envVars;
    }

    private Collection<? extends EnvVar> getEnvVarsFromExecutionUnit() {
        List<EnvVar> envVars = new ArrayList<>();
        for(Map.Entry<String, String> entry : executionUnit.getContainerEnvironmentVariables().entrySet()){
            EnvVar envVar = new EnvVar();
            envVar.setName(entry.getKey());
            envVar.setValue(entry.getValue());
            envVars.add(envVar);
        }
        return envVars;
    }

    private Collection<? extends EnvVar> getEnvVarsFromVariables() {
        long taskId = (Long) executionUnit.getMetadata().get("id");
        ExecutionTask task = this.taskProvider.get(taskId);
        TaoComponent component = TaskUtilities.getComponentFor(task);
        if (!(component instanceof ProcessingComponent nodeComponent)) {
            throw new RuntimeException("Tao component " + component + " is not a processing component");
        }
        List<EnvVar> envVars = new ArrayList<>();
        for(Variable envVarComponent : nodeComponent.getVariables()){
            EnvVar envVar = new EnvVar();
            envVar.setName(envVarComponent.getKey());
            envVar.setValue(envVarComponent.getValue());
            envVars.add(envVar);
        }
        return envVars;
    }

    private WorkflowTemplate getTemplateToBeUpdatedWithContainerInfo() {
        long taskId = (Long) executionUnit.getMetadata().get("id");
        ExecutionTask task = this.taskProvider.get(taskId);
        TaoComponent component = TaskUtilities.getComponentFor(task);
        String executionUnitName = replaceWithConnectorString(component.getId().toLowerCase()) + CONNECTOR_STRING + task.getWorkflowNodeId();
        Optional<WorkflowTemplate> optionalTemplate = argoWorkflow.getSpec().getTemplates()
                .stream()
                .filter(template -> template.getName().contains(executionUnitName))
                .findFirst();
        if(optionalTemplate.isEmpty())
            throw new NullPointerException("No Template exists for the specified execution unit!");
        return optionalTemplate.get();
    }

    private List<String> getBashArguments() {
        List<String> bashArguments;
        if (isBash()) {
            bashArguments = Arrays.asList(this.executionUnit.getArguments().get(2).split(" "));
        } else {
            bashArguments = this.executionUnit.getArguments();
        }
        return bashArguments;
    }

    private List<String> getAllArgumentsForContainer(List<String> bashArguments) {
        List<String> mainArguments = new ArrayList<>();
        mainArguments.add(String.join(" ", bashArguments));
        return mainArguments.stream()
                .map(s -> s.replaceAll("\"", ""))
                .collect(Collectors.toList());
    }

    private List<String> getCommandsForContainer(List<String> bashArguments) {
        List<String> baseCommandArray = new ArrayList<>();
        if(isBash()) {
            baseCommandArray.add("/bin/bash");
            baseCommandArray.add("-c");
        }else {
            baseCommandArray.add(bashArguments.get(0).replaceAll("\"", ""));
            int bashArgsLastNonParamPosition = ListIterate.detectIndex(bashArguments, b -> b.startsWith("-") && !b.equals("-c"));
            if (bashArguments.get(1).startsWith(" \\")) {
                baseCommandArray.add(bashArguments.get(1).replaceAll("\"","").replaceAll("\\\\","/"));
            } else if (bashArguments.get(1).equals("-c")) {
                for(int i = 0 ; i < bashArgsLastNonParamPosition ; i++) {
                    baseCommandArray.add(bashArguments.get(i));
                }
            }
        }
        return baseCommandArray;
    }

    private boolean isBash(){
        final List<String> args = this.executionUnit.getArguments();
        return !args.isEmpty() && args.getFirst().equals("/bin/bash");
    }

    private String getImageForContainer() {
        ContainerUnit containerUnit = this.executionUnit.getContainerUnit();
        if(ExecutionConfiguration.getDockerRegistry().equals(containerUnit.getContainerRegistry()))
            return containerUnit.getContainerName();
        return containerUnit.getContainerRegistry() + "/" + containerUnit.getContainerName();
    }

    private List<WorkflowNodeDescriptor> orderWorkflowNodes(WorkflowDescriptor workflowDesc) {
        List<WorkflowNodeDescriptor> nodes = workflowDesc.orderNodes(workflowDesc.getNodes(),null);
        return nodes.stream().sorted(Comparator.comparing(WorkflowNodeDescriptor::getLevel)).collect(Collectors.toList());
    }
    private WorkflowDescriptor getWorkflowDescriptor(){
        long taskId = (Long) executionUnit.getMetadata().get("id");
        ExecutionTask task = this.taskProvider.get(taskId);
        long workflowId = task.getJob().getWorkflowId();
        return this.workflowProvider.get(workflowId);
    }

    private String getExecutionUnitName(){
        long taskId = (Long) executionUnit.getMetadata().get("id");
        ExecutionTask task = taskProvider.get(taskId);
        TaoComponent component = TaskUtilities.getComponentFor(task);
        return replaceWithConnectorString(component.getId().toLowerCase())
                + CONNECTOR_STRING + task.getWorkflowNodeId();
    }

    private void checkExecutionUnit(){
        if (executionUnit == null)
            throw new NullPointerException("Execution unit is not set");
    }

    private void checkScriptPath(){
        if (executionUnit.getScriptTargetPath() == null)
            throw new RuntimeException("Script Target Path is not set");
    }

    private void checkContainerUnit(){
        if(this.executionUnit.getContainerUnit() == null){
            throw new RuntimeException("Container unit is null");
        }
    }

}
