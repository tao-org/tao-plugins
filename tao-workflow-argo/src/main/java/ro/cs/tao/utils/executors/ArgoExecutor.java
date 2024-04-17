package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.gs.collections.impl.utility.ListIterate;
import io.fabric8.kubernetes.api.model.*;
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
import ro.cs.tao.workflow.ParameterValue;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;
import ro.cs.tao.workflow.enums.ComponentType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ArgoExecutor extends Executor<Object>
        implements ExecutionDescriptorConverter {
    private final static String ARGO_VERSION = "argoproj.io/v1alpha1";
    private final static String ENTRYPOINT = "workflow-start";
    private final static String MAIN_WF_INPUT_PREFIX = "wf-input-";
    private final static String MAIN_WF_OUTPUT_PREFIX = "wf-out-";
    private final static String CONNECTOR_STRING = "-";

    private ExecutionUnit executionUnit;
    private final ExecutionTaskProvider taskProvider;
    private final WorkflowProvider workflowProvider;
    private final ArgoWorkflow argoWorkflow;
    private final Set<WorkflowNodeDescriptor> visitedNodes;

    public ArgoExecutor() {
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.argoWorkflow = new ArgoWorkflow();
        this.visitedNodes = new HashSet<>();
    }

    public ArgoExecutor(ExecutionUnit unit) {
        super(unit.getHost(), unit.getArguments(), unit.asSuperUser());
        this.executionUnit = unit;
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.argoWorkflow = new ArgoWorkflow();
        this.visitedNodes = new HashSet<>();
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
            final Path scriptTargetPath = Paths.get(executionUnit.getScriptTargetPath());
            WorkflowDescriptor workflowDescriptor = getWorkflowDescriptor();
            initializeArgoWorkflow();
            parseNodes(workflowDescriptor);
            updateContainerInfo();
            // synchronize on final variable as the update and create yaml part is a critical section
            synchronized (this.workflowProvider) {
                if (Files.exists(scriptTargetPath)) {
                    updateYaml(scriptTargetPath);
                    logger.fine("Update Argo Graph File");
                    return 0;
                }
                createYaml(scriptTargetPath);
                logger.fine("Create Argo Graph File");
            }
        } catch (Exception e){
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
        return 0;
    }




    private void initializeArgoWorkflow() {
        argoWorkflow.setApiVersion(ARGO_VERSION);
        argoWorkflow.setKind("Workflow");
        final Path scriptTargetPath = Paths.get(executionUnit.getScriptTargetPath());
        initializeWorkflowMetadata(scriptTargetPath);
        initializeWorkflowSpec();
        //argoWorkflow.setStatus(new LinkedHashMap<>());
    }

    private void initializeWorkflowMetadata(Path scriptTargetPath) {
        WorkflowMetadata metadata = new WorkflowMetadata();
        String workflowName = scriptTargetPath.getFileName().toString().substring(0,scriptTargetPath.getFileName().toString().indexOf("."));
        metadata.setGenerateName(workflowName);
        argoWorkflow.setMetadata(metadata);
    }

    private void initializeWorkflowSpec() {
        WorkflowSpec spec = new WorkflowSpec();
        spec.setEntrypoint(ENTRYPOINT);
        initializeSpecVolumes(spec);
        initializeSpecTemplates(spec);
        initializeSpecArguments(spec);
        argoWorkflow.setSpec(spec);
    }

    private void initializeSpecVolumes(WorkflowSpec spec){
        List<Volume> volumes = new ArrayList<>();
        Volume basicOutputVolume = new Volume();
        basicOutputVolume.setName("out-volume");
        basicOutputVolume.setEmptyDir(new EmptyDirVolumeSource());
        volumes.add(basicOutputVolume);
        spec.setVolumes(volumes);
    }

    private void initializeSpecTemplates(WorkflowSpec spec) {
        List<WorkflowTemplate> templates = new ArrayList<>();
        initializeDAGTemplate(templates);
        spec.setTemplates(templates);
    }

    private void initializeDAGTemplate(List<WorkflowTemplate> templates) {
        WorkflowTemplate dagTemplate = new WorkflowTemplate();
        dagTemplate.setName(ENTRYPOINT);
        DAG dag = new DAG();
        initializeDAGTasksList(dag);
        dagTemplate.setDAG(dag);
        templates.add(dagTemplate);
    }

    private void initializeDAGTasksList(DAG dag) {
        dag.setTasks(new ArrayList<>());
    }

    private void initializeSpecArguments(WorkflowSpec spec) {
        WorkflowArguments workflowArguments = new WorkflowArguments();
        spec.setArguments(workflowArguments);
    }



    private void createYaml(Path path) {
        ArgoWorkflowYAMLFactory factory =  new ArgoWorkflowYAMLFactory();
        factory.enable(ArgoWorkflowYAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(ArgoWorkflowYAMLGenerator.Feature.WRITE_DOC_START_MARKER);

        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            Map<String, Object> workflowMap = mapper.convertValue(argoWorkflow, new TypeReference<>() {
            });
            mapper.writeValue(path.toFile(), workflowMap);
        } catch (IOException e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
    }

    private void updateYaml(Path path) {
        ArgoWorkflowYAMLFactory factory =  new ArgoWorkflowYAMLFactory();
        factory.enable(ArgoWorkflowYAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(ArgoWorkflowYAMLGenerator.Feature.WRITE_DOC_START_MARKER);

        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            Map<String, Object> workflowMap= mapper.readValue(path.toFile(), Map.class);
            if (workflowMap != null && !workflowMap.isEmpty()) {
                long taskId = (Long) executionUnit.getMetadata().get("id");
                ExecutionTask task = this.taskProvider.get(taskId);
                TaoComponent component = TaskUtilities.getComponentFor(task);
                String executionUnitName = component.getId().toLowerCase() + CONNECTOR_STRING + task.getWorkflowNodeId();
                List<LinkedHashMap<String,Object>> templates = (ArrayList<LinkedHashMap<String,Object>>)((LinkedHashMap<String, Object>) workflowMap.get("spec")).get("templates");
                Map<String, Object> templateFromFile = new LinkedHashMap<>();
                for(Map<String, Object> templateMap : templates){
                    String templateName = (String) templateMap.get("name");
                    if(templateName.contains(executionUnitName)){
                        templateFromFile = templateMap;
                    }
                }
                Optional<WorkflowTemplate> optionalTemplateFromArgoWorkflow = argoWorkflow.getSpec().getTemplates()
                        .stream()
                        .filter(template -> template.getName().contains(executionUnitName))
                        .findFirst();
                if(optionalTemplateFromArgoWorkflow.isEmpty())
                    throw new NullPointerException("No Template exists for the specified execution unit!");
                WorkflowTemplate templateFromArgo = optionalTemplateFromArgoWorkflow.get();
                Map<String, Object> containerMap = mapper.convertValue(templateFromArgo.getContainer(), new TypeReference<>() {
                });
                templateFromFile.put("container", containerMap);
                mapper.writeValue(path.toFile(), workflowMap);
            }
        } catch (IOException e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
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
                String inputId = nodeComponent.getTargets().get(0).getName() + CONNECTOR_STRING + node.getId();
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
                workflowArtifact.setName(artifactName + CONNECTOR_STRING + i);
                workflowArtifact.setPath(FileUtilities.toUnixPath(strings[i]));
                workflowArtifacts.add(workflowArtifact);
            }
        } else {
            WorkflowArtifact workflowArtifact = new WorkflowArtifact();
            workflowArtifact.setName(artifactName);
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
        List<DAGTask> dagTasks = templates.get(0).getDAG().getTasks();
        DAGTask dagTask = createNewDAGTask(node);
        dagTasks.add(dagTask);
        WorkflowTemplate workflowTemplate = createNewWorkflowTemplate(node);
        templates.add(workflowTemplate);
        computeTemplateDependencies(node, dagTask.getDependencies());
        computeTemplateInputs(node, dagTask, workflowTemplate);

        setNodeReferences(node);
    }


    private DAGTask createNewDAGTask(WorkflowNodeDescriptor currentNode) {
        DAGTask dagTask = new DAGTask();
        initializeDAGTask(dagTask);
        dagTask.setName(currentNode.getComponentId().toLowerCase() + CONNECTOR_STRING + currentNode.getId());
        dagTask.setTemplate(currentNode.getComponentId().toLowerCase() + CONNECTOR_STRING + currentNode.getId());

        return dagTask;
    }

    private void initializeDAGTask(DAGTask dagTask) {
        WorkflowArguments dagTaskArguments = new WorkflowArguments();
        dagTaskArguments.setParameters(new ArrayList<>());
        dagTask.setArguments(dagTaskArguments);
        dagTask.setDependencies(new ArrayList<>());
    }

    private WorkflowTemplate createNewWorkflowTemplate(WorkflowNodeDescriptor currentNode) {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        initializeTemplateInputs(workflowTemplate);
        initializeTemplateOutputs(workflowTemplate);
        workflowTemplate.setName(currentNode.getComponentId().toLowerCase() + CONNECTOR_STRING + currentNode.getId());
        parseTargetsForOutputs(currentNode, workflowTemplate);
        return workflowTemplate;
    }

    private void initializeTemplateInputs(WorkflowTemplate workflowTemplate) {
        TemplateInputs templateInputs = new TemplateInputs();
        templateInputs.setParameters(new ArrayList<>());
        workflowTemplate.setInputs(templateInputs);
    }

    private void initializeTemplateOutputs(WorkflowTemplate workflowTemplate){
        TemplateOutputs templateOutputs = new TemplateOutputs();
        templateOutputs.setArtifacts(new ArrayList<>());
        workflowTemplate.setOutputs(templateOutputs);
    }

    // to refactor
    private void parseTargetsForOutputs(WorkflowNodeDescriptor currentNode, WorkflowTemplate template) {
        List<WorkflowArtifact> outputArtifacts = template.getOutputs().getArtifacts();
        TaoComponent nodeComponent = TaskUtilities.getComponentFor(currentNode);
        for (TargetDescriptor target : nodeComponent.getTargets()) {
            String outParamId = target.getName() + CONNECTOR_STRING + currentNode.getId() ;
            String outputId = currentNode.getComponentId().toLowerCase() + CONNECTOR_STRING + outParamId;
            String templateOutputName = MAIN_WF_OUTPUT_PREFIX + outputId;
            computeTemplateOutputs(target, templateOutputName, outputArtifacts);
        }
    }

    private void computeTemplateInputs(WorkflowNodeDescriptor node, DAGTask dagTask, WorkflowTemplate workflowTemplate) {
        TaoComponent taoComponent = TaskUtilities.getComponentFor(node);
        if(!(taoComponent instanceof ProcessingComponent nodeComponent))
            throw new RuntimeException("Tao component " + taoComponent + " is not a processing component");
        parseSources(nodeComponent, node, dagTask, workflowTemplate);
        parseProcessingParameters(nodeComponent, node, workflowTemplate, dagTask);
        parseCustomValues(node, dagTask, workflowTemplate);
    }

    private void parseSources(ProcessingComponent nodeComponent, WorkflowNodeDescriptor node, DAGTask dagTask, WorkflowTemplate workflowTemplate) {
        List<WorkflowParameter> dagTaskInputParameters = dagTask.getArguments().getParameters();
        List<WorkflowParameter> inputParameters = workflowTemplate.getInputs().getParameters();
        for(SourceDescriptor source : nodeComponent.getSources()){
            WorkflowParameter parameter = new WorkflowParameter();
            parameter.setName(source.getName() + CONNECTOR_STRING + node.getId());
            WorkflowParameter templateParameter = new WorkflowParameter();
            templateParameter.setName(source.getName() + CONNECTOR_STRING + node.getId());
            dagTaskInputParameters.add(parameter);
            inputParameters.add(templateParameter);
        }
    }

    private void parseProcessingParameters(ProcessingComponent nodeComponent, WorkflowNodeDescriptor node, WorkflowTemplate workflowTemplate, DAGTask dagTask) {
        List<WorkflowParameter> dagTaskInputParameters = dagTask.getArguments().getParameters();
        List<WorkflowParameter> inputParameters = workflowTemplate.getInputs().getParameters();
        Set<ParameterDescriptor> paramsToBeAddedAsInputs = new HashSet<>(nodeComponent.getParameterDescriptors());
        for(ParameterDescriptor param : paramsToBeAddedAsInputs){
            if(isParamAlreadyAnInput(param, dagTaskInputParameters)) {
                updateInputDefaultValue(param, dagTaskInputParameters);
                continue;
            }
            WorkflowParameter parameter = new WorkflowParameter();
            parameter.setName(param.getName() + CONNECTOR_STRING + node.getId());
            parameter.setDefaultValue(param.getDefaultValue());
            WorkflowParameter templateParameter = new WorkflowParameter();
            templateParameter.setName(param.getName() + CONNECTOR_STRING + node.getId());
            dagTask.getArguments().getParameters().add(parameter);
            inputParameters.add(templateParameter);
        }
    }

    private boolean isParamAlreadyAnInput(ParameterDescriptor parameter, List<WorkflowParameter> dagTaskInputParameters) {
        Optional<WorkflowParameter> optionalWorkflowParameter = dagTaskInputParameters
                .stream().filter(p -> p.getName().contains(parameter.getName())).findFirst();
        return optionalWorkflowParameter.isPresent();
    }

    private void updateInputDefaultValue(ParameterDescriptor parameter, List<WorkflowParameter> dagTaskInputParameters) {
        Optional<WorkflowParameter> optionalWorkflowParameter = dagTaskInputParameters
                .stream().filter(p -> p.getName().contains(parameter.getName())).findFirst();
        if(optionalWorkflowParameter.isPresent()){
            WorkflowParameter workflowParameter = optionalWorkflowParameter.get();
            workflowParameter.setDefaultValue(parameter.getDefaultValue());
        }
    }


    private void parseCustomValues(WorkflowNodeDescriptor node, DAGTask dagTask, WorkflowTemplate workflowTemplate) {
        List<WorkflowParameter> dagTaskInputParameters = dagTask.getArguments().getParameters();
        List<WorkflowParameter> inputParameters = workflowTemplate.getInputs().getParameters();
        for(ParameterValue param : node.getCustomValues()){
            if(isCustomValueAnOutput(param, workflowTemplate))
                continue;
            if(isCustomValueAlreadyAnInput(param, dagTaskInputParameters)){
                updateInputValue(param, dagTaskInputParameters);
                continue;
            }
            WorkflowParameter inputDAGParameter = new WorkflowParameter();
            inputDAGParameter.setName(param.getParameterName());
            inputDAGParameter.setValue(param.getParameterValue());
            WorkflowParameter inputTemplateParameter = new WorkflowParameter();
            inputTemplateParameter.setName(param.getParameterName());
            inputParameters.add(inputTemplateParameter);
            dagTaskInputParameters.add(inputDAGParameter);
        }
    }

    private boolean isCustomValueAnOutput(ParameterValue param, WorkflowTemplate workflowTemplate) {
        Optional<WorkflowArtifact> optionalArtifact = workflowTemplate.getOutputs().getArtifacts().stream()
                .filter(output -> output.getName().contains(param.getParameterName() + CONNECTOR_STRING))
                .findFirst();
        return optionalArtifact.isPresent();
    }

    private boolean isCustomValueAlreadyAnInput(ParameterValue param, List<WorkflowParameter> dagTaskInputParameters){
        Optional<WorkflowParameter> optionalParam = dagTaskInputParameters.stream().filter(p -> p.getName().contains(param.getParameterName())).findFirst();
        return optionalParam.isPresent();
    }

    private void updateInputValue(ParameterValue param, List<WorkflowParameter> dagTaskInputParameters) {
        Optional<WorkflowParameter> optionalParam = dagTaskInputParameters.stream().filter(p -> p.getName().contains(param.getParameterName())).findFirst();
        if(optionalParam.isPresent()){
            WorkflowParameter parameter = optionalParam.get();
            parameter.setValue(param.getParameterValue());
        }
    }

    private void computeTemplateOutputs(TargetDescriptor target, String templateOutputName, List<WorkflowArtifact> outputArtifacts) {
        if (target.getDataDescriptor().getLocation().contains(",")) {
            String[] strings = target.getDataDescriptor().getLocation().split(",");
            for (int i=0; i < strings.length; i++) {
                WorkflowArtifact workflowArtifact = new WorkflowArtifact();
                workflowArtifact.setName(templateOutputName + CONNECTOR_STRING + i);
                workflowArtifact.setPath(templateOutputName + "/" + FileUtilities.toUnixPath(strings[i]));
                ArchiveStrategy archiveStrategy = new ArchiveStrategy();
                archiveStrategy.setNone(new NoneStrategy());
                workflowArtifact.setArchive(archiveStrategy);
                outputArtifacts.add(workflowArtifact);
            }
        } else {
            WorkflowArtifact workflowArtifact = new WorkflowArtifact();
            workflowArtifact.setName(templateOutputName);
            workflowArtifact.setPath(FileUtilities.toUnixPath(templateOutputName + "/" + target.getDataDescriptor().getLocation()));
            ArchiveStrategy archiveStrategy = new ArchiveStrategy();
            archiveStrategy.setNone(new NoneStrategy());
            workflowArtifact.setArchive(archiveStrategy);
            outputArtifacts.add(workflowArtifact);
        }
    }

    private void computeTemplateDependencies(WorkflowNodeDescriptor currentNode, List<String> dependencies){
        List<WorkflowNodeDescriptor> nodesExceptDataSources = getNodesExceptDataSources();
        for(ComponentLink link : currentNode.getIncomingLinks()){
            String dependencyId = link.getInput().getParentId().toLowerCase() + CONNECTOR_STRING + link.getSourceNodeId();
            for(WorkflowNodeDescriptor node : nodesExceptDataSources){
                String dependencyTemplateName = node.getComponentId().toLowerCase() + CONNECTOR_STRING + node.getId();
                if(dependencyId.equals(dependencyTemplateName)){
                    dependencies.add(dependencyId);
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

    private void setNodeReferences(WorkflowNodeDescriptor currentNode) {
        List<WorkflowArtifact> workflowArtifacts = argoWorkflow.getSpec().getArguments().getArtifacts();
        if(Objects.isNull(workflowArtifacts) || workflowArtifacts.isEmpty())
            return;
        for(ComponentLink link : currentNode.getIncomingLinks()){
            String incomingLinkName = link.getInput().getName() + CONNECTOR_STRING + link.getSourceNodeId();
            String parameterName = link.getOutput().getName() + CONNECTOR_STRING + currentNode.getId();
            // verify of the node is linked to a datasource node(to a WorkflowArtifact)
            Optional<WorkflowArtifact> isLinkedToArtifact = workflowArtifacts.stream()
                    .filter(artifact -> artifact.getName().contains(incomingLinkName))
                    .findFirst();
            if(isLinkedToArtifact.isPresent()){
                setTaskReferenceToWorkflow(isLinkedToArtifact.get(), parameterName);
            } else {
                String templateName = link.getInput().getParentId().toLowerCase() + CONNECTOR_STRING +link.getSourceNodeId();
                setTaskReferenceToTemplateOutput(templateName, incomingLinkName, parameterName);
            }
        }
    }

    private void setTaskReferenceToWorkflow(WorkflowArtifact artifact, String parameterName){
        String dagTaskId = parameterName.split("-")[1];
        List<WorkflowParameter> parameters = getParametersOfDagTaskById(dagTaskId);
        WorkflowParameter dependentParameter = getParameterByName(parameters, parameterName);
        String value = "{{workflow.artifacts." + artifact.getName() + "}}";
        // if the link is dependent on another node, do not overwrite the Reference
        String dependentParameterValue = dependentParameter.getValue();
        if(dependentParameterValue == null || dependentParameterValue.isEmpty())
            dependentParameter.setValue(value);
    }

    private List<WorkflowParameter> getParametersOfDagTaskById(String dagTaskId) {
        List<DAGTask> tasks = argoWorkflow.getSpec().getTemplates().get(0).getDAG().getTasks();
        Optional<DAGTask> dagTaskOptional = tasks.stream()
                .filter(task -> task.getName().contains(dagTaskId))
                .findFirst();
        if(dagTaskOptional.isEmpty())
            throw new NoSuchElementException("Optional DAG task is empty");
        DAGTask dagTask = dagTaskOptional.get();
        return dagTask.getArguments().getParameters();
    }

    private WorkflowParameter getParameterByName(List<WorkflowParameter> parameters, String parameterName) {
        Optional<WorkflowParameter> dependentParameterOptional = parameters.stream()
                .filter(p -> p.getName().contains(parameterName))
                .findFirst();
        if(dependentParameterOptional.isEmpty())
            throw new NoSuchElementException("Optional parameter is empty");
        return dependentParameterOptional.get();
    }

    private void setTaskReferenceToTemplateOutput(String templateName, String incomingLinkName, String parameterName) {
        WorkflowTemplate templateToBeReferenced = getTemplateByName(templateName);
        WorkflowArtifact templateOutput = getTemplateOutputByLinkName(templateToBeReferenced, incomingLinkName);
        List<DAGTask> dagTasks = argoWorkflow.getSpec().getTemplates().get(0).getDAG().getTasks();
        for(DAGTask task : dagTasks){
            for(WorkflowParameter param : task.getArguments().getParameters()){
                if(param.getName().contains(parameterName))
                    param.setValue("{{tasks." + templateToBeReferenced.getName() + ".outputs.artifacts." + templateOutput.getName() + "}}");
            }
        }

    }

    private WorkflowArtifact getTemplateOutputByLinkName(WorkflowTemplate template, String incomingLinkName) {
        Optional<WorkflowArtifact> optionalTemplateOutput = template.getOutputs().getArtifacts()
                .stream()
                .filter(output -> output.getName().contains(incomingLinkName))
                .findFirst();
        if(optionalTemplateOutput.isEmpty())
            throw new NoSuchElementException("Optional Template is empty");
        return optionalTemplateOutput.get();
    }

    private WorkflowTemplate getTemplateByName(String templateName) {
        List<WorkflowTemplate> templates = argoWorkflow.getSpec().getTemplates();
        Optional<WorkflowTemplate> templateOptional = templates.stream()
                .filter(t -> t.getName().equals(templateName))
                .findFirst();
        if(templateOptional.isEmpty())
            throw new NoSuchElementException("Optional Template is empty");
        return templateOptional.get();
    }

    private void updateContainerInfo() {
        checkContainerUnit();
        WorkflowTemplate currentTemplate = getTemplateToBeUpdatedWithContainerInfo();
        Container container = new Container();
        currentTemplate.setContainer(container);
        List<String> bashArguments = getBashArguments();
        container.setArgs(getArgumentsForContainer(currentTemplate, bashArguments));
        container.setEnv(getEnvVarForContainer());
        container.setCommand(getCommandsForContainer(bashArguments));
        container.setImage(getImageForContainer());
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
        if(!(component instanceof ProcessingComponent nodeComponent))
            throw new RuntimeException("Tao component " + component + " is not a processing component");
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
        String executionUnitName = component.getId().toLowerCase() + CONNECTOR_STRING + task.getWorkflowNodeId();
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
        final boolean isBash = this.executionUnit.getArguments().get(0).equals("/bin/bash");
        if (isBash) {
            bashArguments = Arrays.asList(this.executionUnit.getArguments().get(2).split(" "));
        } else {
            bashArguments = this.executionUnit.getArguments();
        }
        return bashArguments;
    }

    private List<String> getArgumentsForContainer(WorkflowTemplate currentTemplate, List<String> bashArguments) {
        List<String> mainArguments = new ArrayList<>();
        StringBuilder extraArguments = new StringBuilder();
        for(int i=1; i < bashArguments.size(); i++){
            String bashArgument = bashArguments.get(i);
            if(bashArgument.startsWith("-")){
                Optional<WorkflowParameter> workflowParameterOptional = currentTemplate.getInputs().getParameters().stream()
                        .filter(p -> p.getName().contains(bashArgument.substring(1)))
                        .findFirst();
                if(workflowParameterOptional.isPresent()){
                    mainArguments.add(bashArgument + " " + "{{" + currentTemplate.getName() + ".inputs." + workflowParameterOptional.get().getName() + "}}");
                } else if(i < bashArguments.size()-1) {
                    mainArguments.add(bashArgument + " " + bashArguments.get(i+1));
                }
                i++;
            }
            while(!bashArgument.startsWith("-") && i < bashArguments.size()) {
                extraArguments.append(" ").append(bashArguments.get(i));
                i++;
            }
            if(mainArguments.isEmpty()){
                mainArguments.add(extraArguments.toString());
            }else {
                mainArguments.set(mainArguments.size() - 1, mainArguments.get(mainArguments.size() - 1).concat(extraArguments.toString()));
            }
            extraArguments.setLength(0);
        }
        mainArguments.addAll(executionUnit.getContainerArguments());
        return mainArguments;
    }

    private List<String> getCommandsForContainer(List<String> bashArguments) {
        List<String> baseCommandArray = new ArrayList<>();
        // first bash argument is always added
        baseCommandArray.add("/bin/bash");
        baseCommandArray.add(bashArguments.get(0).replaceAll("\"", ""));
        int bashArgsLastNonParamPosition = ListIterate.detectIndex(bashArguments, b -> b.startsWith("-") && !b.equals("-c"));
        if (bashArguments.get(1).startsWith(" \\")) {
            baseCommandArray.add(bashArguments.get(1).replaceAll("\"","").replaceAll("\\\\","/"));
        } else if (bashArguments.get(1).equals("-c")) {
            for(int i = 1 ; i < bashArgsLastNonParamPosition ; i++) {
                baseCommandArray.add(bashArguments.get(i));
            }
        }
        return baseCommandArray;
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
