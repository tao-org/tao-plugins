package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.gs.collections.impl.utility.ListIterate;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.Volume;
import ro.cs.tao.argo.workflow.model.*;
import ro.cs.tao.component.ComponentLink;
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.component.TargetDescriptor;
import ro.cs.tao.datasource.DataSourceComponent;
import ro.cs.tao.datasource.beans.Parameter;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.execution.model.ExecutionJob;
import ro.cs.tao.execution.model.ExecutionStatus;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.persistence.ExecutionJobProvider;
import ro.cs.tao.execution.persistence.ExecutionTaskProvider;
import ro.cs.tao.execution.util.TaskUtilities;
import ro.cs.tao.persistence.WorkflowNodeProvider;
import ro.cs.tao.persistence.WorkflowProvider;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
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
    private final static String ENTRYPOINT = "workflow_start";
    private final static String MAIN_WF_INPUT_PREFIX = "wf_input_";
    private final static String MAIN_WF_OUTPUT_PREFIX = "wf_out_";

    private ExecutionUnit executionUnit;
    private final ExecutionTaskProvider taskProvider;
    private final WorkflowProvider workflowProvider;
    private final WorkflowNodeProvider workflowNodeProvider;
    private final ExecutionJobProvider jobProvider;
    private final ArgoWorkflow argoWorkflow;

    public ArgoExecutor() {
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.workflowNodeProvider = SpringContextBridge.services().getService(WorkflowNodeProvider.class);
        this.jobProvider = SpringContextBridge.services().getService(ExecutionJobProvider.class);
        this.argoWorkflow = new ArgoWorkflow();
    }

    public ArgoExecutor(ExecutionUnit unit) {
        super(unit.getHost(), unit.getArguments(), unit.asSuperUser());
        this.executionUnit = unit;
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.workflowNodeProvider = SpringContextBridge.services().getService(WorkflowNodeProvider.class);
        this.jobProvider = SpringContextBridge.services().getService(ExecutionJobProvider.class);
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
    public int execute(boolean logMessages) throws Exception {
        ensureExecutionUnit();
        ensureScriptPath();

        try{
            final Path scriptTargetPath = Paths.get(executionUnit.getScriptTargetPath());

            if (Files.exists(scriptTargetPath)) {
                return 0;
            }
            WorkflowDescriptor workflowDescriptor = getWorkflowDescriptor();
            initializeArgoWorkflow();
            parseNodes(workflowDescriptor);
            createYAMLFile(scriptTargetPath);
            System.out.println("Create Argo Graph File");
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

    //how to add volumes, what are volumes in tao-context?
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



    private void createYAMLFile(Path path) {

        ArgoWorkflowYAMLFactory factory =  new ArgoWorkflowYAMLFactory();
        factory.enable(ArgoWorkflowYAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(ArgoWorkflowYAMLGenerator.Feature.WRITE_DOC_START_MARKER);

        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
//            if (Files.exists(path)) {
//                Map<Object, Object> existingInputList = mapper.readValue(path.toFile(), Map.class);
//                if (existingInputList != null && existingInputList.size() > 0) {
//                    tempList.putAll(existingInputList);
//                }
//            }
            mapper.writeValue(path.toFile(), argoWorkflow);

        } catch (IOException e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
    }

    private void setReferences(WorkflowNodeDescriptor currentNode, WorkflowArguments workflowArguments, List<DAGTask> tasks) {
        List<WorkflowArtifact> workflowArtifacts = workflowArguments.getArtifacts();
        if(Objects.isNull(workflowArtifacts) || workflowArtifacts.isEmpty())
            return;
        for(ComponentLink link : currentNode.getIncomingLinks()){
            String dependencyName = link.getInput().getName();
            String parameterName = link.getOutput().getName();
            findParameterToChange(workflowArtifacts, tasks, dependencyName, parameterName, link);
        }
    }

    private void findParameterToChange(List<WorkflowArtifact> workflowArtifacts, List<DAGTask> tasks, String dependencyName, String parameterName, ComponentLink link){
        for(WorkflowArtifact artifact : workflowArtifacts){
            if(artifact.getName().contains(dependencyName + "_" + link.getSourceNodeId())){
                Optional<WorkflowParameter> dependentParameterOptional = tasks.stream()
                        .flatMap(task -> task.getArguments().getParameters().stream())
                        .filter(param -> param.getName().contains(parameterName))
                        .findFirst();
                if(dependentParameterOptional.isEmpty())
                    throw new NoSuchElementException("Optional parameter is empty");
                WorkflowParameter dependentParameter = dependentParameterOptional.get();
                String value = "{{workflow.artifacts." + artifact.getName() + "}}";
                dependentParameter.setValue(value);
            }
        }
    }

    private void parseNodes(WorkflowDescriptor workflowDescriptor){
        List<WorkflowNodeDescriptor> roots = workflowDescriptor.findRoots(workflowDescriptor.getNodes());
        for (WorkflowNodeDescriptor node  : roots) {
            List<WorkflowNodeDescriptor> nodesToBeVisited = new ArrayList<>();
            nodesToBeVisited.add(node);
            parseSourceDescriptorsForInputs(node);
            while(!nodesToBeVisited.isEmpty()) {
                WorkflowNodeDescriptor nodeS = nodesToBeVisited.remove(0);
                List<WorkflowNodeDescriptor> children = workflowDescriptor.findChildren(workflowDescriptor.getNodes(), nodeS);
                nodesToBeVisited.addAll(children);
                parseNode(nodeS);
            }
        }
    }

    private void parseNode(WorkflowNodeDescriptor node){
        //DatSources will be skipped
        if(node.getComponentType().equals(ComponentType.DATASOURCE))
            return;
        // de verificat
        List<WorkflowTemplate> templates = argoWorkflow.getSpec().getTemplates();
        List<DAGTask> dagTasks = templates.get(0).getDAG().getTasks();
        DAGTask dagTask = createNewDAGTask(node);
        dagTasks.add(dagTask);
        WorkflowTemplate workflowTemplate = createNewWorkflowTemplate(node);
        templates.add(workflowTemplate);
        parseLinksForDependencies(node, dagTask.getDependencies());
        parseSourcesForTaskTemplateInputs(node, dagTask, workflowTemplate);
        parseCustomValuesForTaskTemplateInputs(node, dagTask, workflowTemplate);
        setContainerForNode(node, workflowTemplate);
        setReferences(node, argoWorkflow.getSpec().getArguments(), dagTasks);

    }

    private void setContainerForNode(WorkflowNodeDescriptor currentNode, WorkflowTemplate currentTemplate) {
        ensureContainerUnit();
        Container container = new Container();
        currentTemplate.setContainer(container);

        // work in progress for container information setting
        long taskId = (Long) executionUnit.getMetadata().get("id");
        ExecutionTask task = this.taskProvider.get(taskId);
        long workflowId = task.getJob().getWorkflowId();
       List<ExecutionJob> jobs = jobProvider.list(ExecutionStatus.QUEUED_ACTIVE);
       WorkflowDescriptor workflowDescriptor = workflowProvider.get(workflowId);
        TaoComponent component = TaskUtilities.getComponentFor(task);

        List<String> bashArguments;
        final boolean isBash = this.executionUnit.getArguments().get(0).equals("/bin/bash");
        if (isBash) {
            bashArguments = Arrays.asList(this.executionUnit.getArguments().get(2).split(" "));
        } else {
            bashArguments = this.executionUnit.getArguments();
        }
        List<String> mainArguments = new ArrayList<>();
        StringBuilder extraArguments = new StringBuilder();
        for(int i=1; i < bashArguments.size(); i++){
            String bashArgument = bashArguments.get(i);
            if(bashArgument.startsWith("-")){
                Optional<WorkflowParameter> workflowParameterOptional = currentTemplate.getInputs().getParameters().stream()
                        .filter(p -> p.getName().contains(bashArgument.substring(1)))
                        .findFirst();
                if(workflowParameterOptional.isPresent()){
                    mainArguments.add(bashArgument + " " + "{{" + currentTemplate.getName() + ".inputs" + workflowParameterOptional.get().getName() + "}}");
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
        ContainerUnit containerUnit = this.executionUnit.getContainerUnit();
        if(ExecutionConfiguration.getDockerRegistry().equals(containerUnit.getContainerRegistry())) {
            container.setImage(containerUnit.getContainerName());
        }else{
            container.setImage(containerUnit.getContainerRegistry() + "/" + containerUnit.getContainerName());
        }
        container.setCommand(baseCommandArray);
        container.setArgs(mainArguments);
    }




    private DAGTask createNewDAGTask(WorkflowNodeDescriptor currentNode) {
        DAGTask dagTask = new DAGTask();
        initializeDAGTask(dagTask);
        dagTask.setName(currentNode.getComponentId().toLowerCase() + "_" + currentNode.getId());
        dagTask.setTemplate(currentNode.getComponentId().toLowerCase() + "_" + currentNode.getId());

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
        workflowTemplate.setName(currentNode.getComponentId().toLowerCase());

        List<WorkflowArtifact> outputArtifacts = workflowTemplate.getOutputs().getArtifacts();
        TaoComponent nodeComponent = TaskUtilities.getComponentFor(currentNode);
        parseTargetDescriptorsForOutputs(currentNode, nodeComponent, outputArtifacts);
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

    private void parseTargetDescriptorsForOutputs(WorkflowNodeDescriptor currentNode, TaoComponent nodeComponent, List<WorkflowArtifact> outputArtifacts) {
        for (TargetDescriptor target : nodeComponent.getTargets()) {
            String outParamId = target.getName() + "_" + currentNode.getId() ;
            String outputId = currentNode.getComponentId().toLowerCase() + "_" + outParamId;
            String templateOutputName = MAIN_WF_OUTPUT_PREFIX + outputId;
            computeWorkflowTemplateOutputArtifacts(target, templateOutputName, outputArtifacts);
        }
    }


    private void parseSourcesForTaskTemplateInputs(WorkflowNodeDescriptor currentNode, DAGTask dagTask, WorkflowTemplate workflowTemplate) {
        TaoComponent nodeComponent = TaskUtilities.getComponentFor(currentNode);
        List<WorkflowParameter> dagTaskInputParameters = dagTask.getArguments().getParameters();
        List<WorkflowParameter> inputParameters = workflowTemplate.getInputs().getParameters();
        //parse parameters for parameters
        for(SourceDescriptor source : nodeComponent.getSources()){
            WorkflowParameter parameter = new WorkflowParameter();
            parameter.setName(source.getName() + "_" + currentNode.getId());
            WorkflowParameter templateParameter = new WorkflowParameter();
            templateParameter.setName(source.getName() + "_" + currentNode.getId());
            dagTaskInputParameters.add(parameter);
            inputParameters.add(templateParameter);
        }
    }

    private void parseCustomValuesForTaskTemplateInputs(WorkflowNodeDescriptor node, DAGTask dagTask, WorkflowTemplate workflowTemplate) {
        List<WorkflowParameter> dagTaskInputParameters = dagTask.getArguments().getParameters();
        List<WorkflowParameter> inputParameters = workflowTemplate.getInputs().getParameters();
        for(ParameterValue param : node.getCustomValues()){
            WorkflowParameter inputDAGParameter = new WorkflowParameter();
            inputDAGParameter.setName(param.getParameterName());
            inputDAGParameter.setValue(param.getParameterValue());
            WorkflowParameter inputTemplateParameter = new WorkflowParameter();
            inputTemplateParameter.setName(param.getParameterName());
            inputParameters.add(inputTemplateParameter);
            dagTaskInputParameters.add(inputDAGParameter);
        }
    }



    private void computeWorkflowTemplateOutputArtifacts(TargetDescriptor target, String templateOutputName, List<WorkflowArtifact> outputArtifacts) {
        if (target.getDataDescriptor().getLocation().contains(",")) {
            String[] strings = target.getDataDescriptor().getLocation().split(",");
            for (int i=0; i < strings.length; i++) {
                WorkflowArtifact workflowArtifact = new WorkflowArtifact();
                workflowArtifact.setName(templateOutputName + "_" + i);
                workflowArtifact.setPath(FileUtilities.toUnixPath(strings[i]));
                ArchiveStrategy archiveStrategy = new ArchiveStrategy();
                archiveStrategy.setNone("{}");
                workflowArtifact.setArchive(archiveStrategy);
                outputArtifacts.add(workflowArtifact);
            }
        } else {
            WorkflowArtifact workflowArtifact = new WorkflowArtifact();
            workflowArtifact.setName(templateOutputName);
            workflowArtifact.setPath(FileUtilities.toUnixPath(target.getDataDescriptor().getLocation()));
            ArchiveStrategy archiveStrategy = new ArchiveStrategy();
            archiveStrategy.setNone("{}");
            workflowArtifact.setArchive(archiveStrategy);
            outputArtifacts.add(workflowArtifact);
        }
    }

    private void parseSourceDescriptorsForInputs(WorkflowNodeDescriptor node) {
        TaoComponent nodeComponent = TaskUtilities.getComponentFor(node);
        for (SourceDescriptor source : nodeComponent.getSources()) {
            if (DataSourceComponent.QUERY_PARAMETER.equals(source.getName())) {
                String inputId = nodeComponent.getTargets().get(0).getName() + "_" + node.getId();
                String artifactName = MAIN_WF_INPUT_PREFIX + inputId;
                computeSpecArtifacts(source, artifactName);
            }
        }
    }

    private void computeSpecArtifacts(SourceDescriptor source, String artifactName){
        WorkflowArguments specArguments = argoWorkflow.getSpec().getArguments();
        List<WorkflowArtifact> workflowArtifacts = new ArrayList<>();
        if (source.getDataDescriptor().getLocation().contains(",")) {
            String[] strings = source.getDataDescriptor().getLocation().split(",");
            for (int i=0; i < strings.length; i++) {
                WorkflowArtifact workflowArtifact = new WorkflowArtifact();
                workflowArtifact.setName(artifactName + "_" + i);
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

    private void parseLinksForDependencies(WorkflowNodeDescriptor currentNode, List<String> dependencies){
        WorkflowDescriptor workflowDescriptor = getWorkflowDescriptor();
        //list of dependencies of all nodes except datasource Roots
        List<WorkflowNodeDescriptor> nodesExceptDataSources = workflowDescriptor.getNodes();
        List<WorkflowNodeDescriptor> roots = workflowDescriptor.findRoots(workflowDescriptor.getNodes());
        List<WorkflowNodeDescriptor> dataSourceRootNodes =
                roots.stream()
                        .filter(r -> r.getComponentType().equals(ComponentType.DATASOURCE))
                        .toList();
        nodesExceptDataSources.removeAll(dataSourceRootNodes);


        for(ComponentLink link : currentNode.getIncomingLinks()){
            String dependencyId = link.getInput().getParentId().toLowerCase() + "_" + link.getSourceNodeId();
            for(WorkflowNodeDescriptor leafNode : nodesExceptDataSources){
                String dependencyTemplateName = leafNode.getComponentId().toLowerCase() + "_" + leafNode.getId();
                if(dependencyId.equals(dependencyTemplateName)){
                    dependencies.add(dependencyId);
                }
            }
        }
    }

    private WorkflowDescriptor getWorkflowDescriptor(){
        long taskId = (Long) executionUnit.getMetadata().get("id");
        ExecutionTask task = this.taskProvider.get(taskId);
        long workflowId = task.getJob().getWorkflowId();
        return this.workflowProvider.get(workflowId);
    }

    private void ensureExecutionUnit(){
        if (executionUnit == null)
            throw new NullPointerException("Execution unit is not set");
    }

    private void ensureScriptPath(){
        if (executionUnit.getScriptTargetPath() == null)
            throw new RuntimeException("Script Target Path is not set");
    }

    private void ensureContainerUnit(){
        if(this.executionUnit.getContainerUnit() == null){
            throw new RuntimeException("Container unit is null");
        }
    }

}
