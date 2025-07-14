package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.gs.collections.impl.utility.ListIterate;
import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.utils.RootLoader;
import ro.cs.tao.component.*;
import ro.cs.tao.datasource.DataSourceComponent;
import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.docker.ExecutionConfiguration;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.persistence.ExecutionTaskProvider;
import ro.cs.tao.execution.util.TaskUtilities;
import ro.cs.tao.persistence.WorkflowNodeProvider;
import ro.cs.tao.persistence.WorkflowProvider;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CWLExecutor extends Executor<Object>
        implements ExecutionDescriptorConverter {

    private ExecutionUnit executionUnit;
    private final ExecutionTaskProvider taskProvider;
    private final WorkflowProvider workflowProvider;
    private final CWLTemplate cwlTemplate;
    private final Set<WorkflowNodeDescriptor> visitedNodes;

    private final static String CWL_GRAPH_ID = "$graph";
    private final static String YAML_FILE_NAME = "inputs.yaml";
    private final static String MAIN_WF_INPUT_ID_PREFIX = "wf_in_";
    private final static String MAIN_WF_OUTPUT_ID_PREFIX = "wf_out_";
    private final static String MAIN_WF_OUTPUT_SOURCE_PREFIX = "workflow_";
    private final static String MAIN_WF_ID = "workflow_start";

    private String cwlWorkflowId;
    private String mainWfStepId;

    public CWLExecutor() {
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.cwlTemplate = new CWLTemplate();
        this.visitedNodes = new HashSet<>();
    }

    public CWLExecutor(ExecutionUnit unit) {
        super(unit.getHost(), unit.getArguments(), unit.asSuperUser());
        this.executionUnit = unit;
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.cwlTemplate = new CWLTemplate();
        this.visitedNodes = new HashSet<>();
    }

    @Override
    public boolean isIntendedFor(ExecutionUnitFormat executionUnitFormat) {
        return executionUnitFormat == ExecutionUnitFormat.CWL;
    }

    @Override
    public boolean canConnect() {
        return true;
    }

    @Override
    public int execute(boolean b) throws Exception {
        if(this.executionUnit.getContainerUnit() == null){
            throw new RuntimeException("Container unit is null");
        }
        if (this.executionUnit.getScriptTargetPath() == null) {
            throw new RuntimeException("Script Target Path is not set");
        }
        try {
            long taskId = (Long) executionUnit.getMetadata().get("id");
            ExecutionTask task = this.taskProvider.get(taskId);
            final Path path = Paths.get(executionUnit.getScriptTargetPath());
            cwlWorkflowId = path.getFileName().toString().substring(0,path.getFileName().toString().indexOf("."));
            mainWfStepId = MAIN_WF_OUTPUT_SOURCE_PREFIX + cwlWorkflowId;

            long workflowId = task.getJob().getWorkflowId();
            WorkflowDescriptor workflowDesc = this.workflowProvider.get(workflowId);
            cwlTemplate.setCwlVersion(CWLVersion.V1_2.toString());
            cwlTemplate.setCwlMainGraphObject(CWL_GRAPH_ID);

            synchronized (this.workflowProvider) {
                if (Files.exists(path)) {
                    updateCWL(task, executionUnit);
                    logger.info("Update CWL File");
                    return 0;
                }
                createCWL(workflowDesc, task, executionUnit);
                logger.info("Create CWL File");
            }
        } catch (Exception e){
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
        return 0;
    }

    private void createCWL (WorkflowDescriptor workflowDesc, ExecutionTask task, ExecutionUnit executionUnit) {
        final Path path = Paths.get(executionUnit.getScriptTargetPath());
        /* Create the root workflow template. This defines:
         * the initial inputs and final outputs
         * requirements and only one step (the workflow that defines the next operations)
         */
        final Workflow mainWorkflow = new WorkflowImpl();
        mainWorkflow.setClass_(Workflow_class.WORKFLOW);
        mainWorkflow.setId(Optional.of(MAIN_WF_ID));

        // add requirements to main Workflow file
        SubworkflowFeatureRequirement subWfFeatureReq = new SubworkflowFeatureRequirementImpl();
        subWfFeatureReq.setClass_(SubworkflowFeatureRequirement_class.SUBWORKFLOWFEATUREREQUIREMENT);

        ScatterFeatureRequirement scatterFeatureReq = new ScatterFeatureRequirementImpl();
        scatterFeatureReq.setClass_(ScatterFeatureRequirement_class.SCATTERFEATUREREQUIREMENT);

        mainWorkflow.setRequirements(Optional.of(new LinkedList<>(Arrays.asList(subWfFeatureReq, scatterFeatureReq))));
        mainWorkflow.setSteps(new LinkedList<>(List.of(new WorkflowStepImpl(mainWfStepId))));

        /* Create workflow template (called by the root workflow).
         * This is the workflow that defines the next operations
         */
        final Workflow cwlWorkflow = new WorkflowImpl();
        cwlWorkflow.setClass_(Workflow_class.WORKFLOW);
        cwlWorkflow.setId(Optional.of(cwlWorkflowId));

        // add requirements to second Workflow
        MultipleInputFeatureRequirement multipleInputFeatureReq = new MultipleInputFeatureRequirementImpl();
        multipleInputFeatureReq.setClass_(MultipleInputFeatureRequirement_class.MULTIPLEINPUTFEATUREREQUIREMENT);

        cwlWorkflow.setRequirements(Optional.of(new LinkedList<>(Arrays.asList(scatterFeatureReq, multipleInputFeatureReq))));

        cwlTemplate.getCwlMainGraphObject().get(CWL_GRAPH_ID).add(mainWorkflow);
        cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).add(cwlWorkflow);

        parseNodes(workflowDesc);

        /*
         * Update root workflow step
         */
        WorkflowStep mainWorkflowStep = mainWorkflow.getStep(mainWfStepId);

        // set run parameter for step found in main workflow
        mainWorkflowStep.setRun(cwlWorkflow.getId().get());
        // set scatter and scatterMethod for step found in main workflow
        mainWorkflowStep.setScatter(mainWorkflowStep.getIn().stream().map(s -> ((WorkflowStepInput)s).getId().get()).collect(Collectors.toList()));
        mainWorkflowStep.setScatterMethod(Optional.of(ScatterMethod.DOTPRODUCT));

        createStepFromTask(task);

        writeToFile(path, cwlTemplate.save());
    }

    private void updateCWL (ExecutionTask task, ExecutionUnit executionUnit) {
        final Path path = Paths.get(executionUnit.getScriptTargetPath());
        Object graphElements = RootLoader.loadDocument(path);
        cwlTemplate.getCwlMainGraphObject().get(CWL_GRAPH_ID).addAll((Collection<?>) graphElements);
        createStepFromTask(task);
        writeToFile(path, cwlTemplate.save());
    }

    /**
     * Create or update the YAML file associated with the current job CWL file
     */
    private void createOrUpdateYamlFile (String key, Object yamlObj) {
        Map<Object, Object> tempList = new LinkedHashMap<>();

        final Path path = Paths.get(executionUnit.getScriptTargetPath());
        String yamlPathParent = path.getParent().toString();
        final Path yamlFile =  Paths.get(yamlPathParent + File.separator + YAML_FILE_NAME);

        MyYAMLFactory factory =  new MyYAMLFactory();
        factory.enable(MyYAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(MyYAMLGenerator.Feature.WRITE_DOC_START_MARKER);

        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        try {
            if (yamlObj != null) {
                if (Files.exists(yamlFile)) {
                    Map<?, ?> existingInputList = mapper.readValue(yamlFile.toFile(), Map.class);
                    if (existingInputList != null && !existingInputList.isEmpty()) {
                        tempList.putAll(existingInputList);
                    }
                }

                if (yamlObj instanceof SourceDescriptor yamlObjTemp) {
                    String inputType = getInOutType(yamlObjTemp.getDataDescriptor().getFormatType(), yamlObjTemp.getDataDescriptor().getLocation());
                    if (yamlObjTemp.getDataDescriptor().getLocation().contains(",")) {
                        String[] strings = yamlObjTemp.getDataDescriptor().getLocation().split(",");
                        List<Object> locationList = new ArrayList<>();
                        for (String sourceStr : strings) {
                            locationList.add(Map.of(
                                    "class", inputType,
                                    "path", FileUtilities.toUnixPath(sourceStr)
                            ));
                        }
                        tempList.put(key, locationList);
                    } else {
                        tempList.put(key, Map.of(
                                "class", inputType,
                                "path", FileUtilities.toUnixPath(yamlObjTemp.getDataDescriptor().getLocation())
                        ));
                    }
                } else {
                    tempList.put(key, yamlObj);
                }
                mapper.writeValue(yamlFile.toFile(), tempList);
            }
        } catch (IOException e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
    }

    private void parseNodes (WorkflowDescriptor workflowDesc) {
        List<WorkflowNodeDescriptor> orderedWfNodeDescList = orderWfDescNodes(workflowDesc);
        List<WorkflowNodeDescriptor> roots = workflowDesc.findRoots(orderedWfNodeDescList);
        List<WorkflowNodeDescriptor> leaves = workflowDesc.findLeaves(orderedWfNodeDescList);
        for (WorkflowNodeDescriptor wf : orderedWfNodeDescList) {
            visitedNodes.add(wf);
            TaoComponent wfComponent = TaskUtilities.getComponentFor(wf);

            for (SourceDescriptor sourceDescriptor : wfComponent.getSources()) {
                //if root node add input information to workflow template
                if (roots.contains(wf)) {
                    setupWorkflowInfoFromRootNode(wfComponent, sourceDescriptor, wf.getId());
                }
            }

            for (TargetDescriptor targetDescriptor : wfComponent.getTargets()) {
                //if leave node add output information to workflow template
                if (leaves.contains(wf)) {
                    setupWorkflowInfoFromLeavesNode(targetDescriptor, wf.getId());
                }
            }

            if (!wf.getIncomingLinks().isEmpty()) {
                describeWorkflowSteps(wf);
            }
            System.out.println("Other node");
        }
        System.out.println("Other workflow descriptor");
    }

    private void setupWorkflowInfoFromRootNode (TaoComponent wfComponent, SourceDescriptor sourceDescriptor, Long workflowId) {
        if (DataSourceComponent.QUERY_PARAMETER.equals(sourceDescriptor.getName())) {
            Workflow mainWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, MAIN_WF_ID);
            Workflow cwlWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, cwlWorkflowId);
            String inputType = getInOutType(sourceDescriptor.getDataDescriptor().getFormatType(), sourceDescriptor.getDataDescriptor().getLocation());
            String inputId = wfComponent.getTargets().getFirst().getName() + "_" + workflowId;
            String mainWfInputId = MAIN_WF_INPUT_ID_PREFIX + inputId;

            WorkflowInputParameter mainWfInput = new WorkflowInputParameterImpl();
            mainWfInput.setId(Optional.of(mainWfInputId));
            mainWfInput.setType(inputType + "[]");

            mainWorkflow.getInputs().add(mainWfInput);

            WorkflowInputParameter cwlWfInput = new WorkflowInputParameterImpl();
            cwlWfInput.setType(inputType);
            cwlWfInput.setId(Optional.of(inputId));
            cwlWorkflow.getInputs().add(cwlWfInput);

            // add input to main workflow step
            WorkflowStepInput stepIn = new WorkflowStepInputImpl();
            stepIn.setId(Optional.of(inputId));
            stepIn.setDefault(Optional.of(mainWfInputId));
            mainWorkflow.getStep(mainWfStepId).getIn().add(stepIn);

            createOrUpdateYamlFile(mainWfInputId, sourceDescriptor);
        }
    }

    private void setupWorkflowInfoFromLeavesNode (TargetDescriptor targetDescriptor, Long workflowId) {
        Workflow mainWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, MAIN_WF_ID);
        Workflow cwlWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, cwlWorkflowId);
        String outParamId = targetDescriptor.getName() + "_" + workflowId;
        String outputType = getInOutType(targetDescriptor.getDataDescriptor().getFormatType(), targetDescriptor.getDataDescriptor().getLocation());
        String outputId = targetDescriptor.getParentId().toLowerCase() + "_" + outParamId;

        String outputSource = targetDescriptor.getParentId().toLowerCase() + "_" + workflowId + "/" + outParamId;

        String mainWfOutputId = MAIN_WF_OUTPUT_ID_PREFIX + outputId;
        String mainOutputSource = MAIN_WF_OUTPUT_SOURCE_PREFIX + cwlWorkflowId + "/" + outputId;

        WorkflowOutputParameter mainWfOutput = new WorkflowOutputParameterImpl();
        mainWfOutput.setId(Optional.of(mainWfOutputId));
        mainWfOutput.setType(outputType + "[]");
        mainWfOutput.setOutputSource(mainOutputSource);
        mainWorkflow.getOutputs().add(mainWfOutput);

        WorkflowOutputParameter output = new WorkflowOutputParameterImpl();
        output.setId(Optional.of(outputId));
        output.setType(outputType);
        output.setOutputSource(outputSource);
        cwlWorkflow.getOutputs().add(output);

        // add input to main workflow step
        WorkflowStepOutput stepOut = new WorkflowStepOutputImpl();
        stepOut.setId(Optional.of(outputId));
        mainWorkflow.getStep(mainWfStepId).getOut().add(stepOut);
    }

    private void describeWorkflowSteps (WorkflowNodeDescriptor wf) {
        Workflow cwlWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, cwlWorkflowId);
        WorkflowStep step = new WorkflowStepImpl();
        String stepId = wf.getComponentId().toLowerCase() + "_" + wf.getId();
        List<Object> inList = new ArrayList<>();
        List<Object> outList = new ArrayList<>();
        for (ComponentLink link : wf.getIncomingLinks()) {
            WorkflowStepInput in = new WorkflowStepInputImpl();
            in.setId(Optional.of(link.getOutput().getName() + "_" + wf.getId()));
            WorkflowNodeDescriptor linkedNode = visitedNodes.stream().filter(n -> n.getId().equals(link.getSourceNodeId())).findFirst().get();
            if (!linkedNode.getIncomingLinks().isEmpty()) {
                String linkedInput = linkedNode.getComponentId().toLowerCase() + "_" + linkedNode.getId();
                in.setDefault(Optional.of(linkedInput + "/" + link.getInput().getName() + "_" + link.getSourceNodeId()));
            } else {
                in.setDefault(Optional.of(link.getInput().getName() + "_" + link.getSourceNodeId()));
            }
            inList.add(in);
        }
        WorkflowNodeDescriptor wfLinkedNode = visitedNodes.stream().filter(n -> n.getId().equals(wf.getId())).findFirst().get();
        TaoComponent wfTaskComponent = TaskUtilities.getComponentFor(wfLinkedNode);
        for (TargetDescriptor targetDescriptor : wfTaskComponent.getTargets()) {
            WorkflowStepOutput out = new WorkflowStepOutputImpl();
            out.setId(Optional.of(targetDescriptor.getName() + "_" + wf.getId()));
            outList.add(out);
        }
        step.setId(Optional.of(stepId));
        step.setRun(stepId);
        step.setIn(inList);
        step.setOut(outList);
        cwlWorkflow.getSteps().add(step);
    }

    private List<Object> createCommandArray (List<String> bashArguments) {
        List<Object> baseCommandArray = new ArrayList<>();
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

    private List<Object> createReqListForCommandToolSteps () {
        List<Object> requirementsList = new ArrayList<>();
        if (this.executionUnit.getMinDisk() != null || this.executionUnit.getMinMemory() != null) {
            ResourceRequirement requirement = new ResourceRequirementImpl();
            requirement.setClass_(ResourceRequirement_class.RESOURCEREQUIREMENT);
            requirement.setRamMin(this.executionUnit.getMinMemory() != null ? this.executionUnit.getMinMemory() : this.executionUnit.getMinDisk());
            requirementsList.add(requirement);
        }
        if (this.executionUnit.getContainerUnit() != null) {
            DockerRequirement requirement = getDockerRequirement();
            requirementsList.add(requirement);
        }
        return requirementsList;
    }

    private DockerRequirement getDockerRequirement() {
        DockerRequirement requirement = new DockerRequirementImpl();
        requirement.setClass_(DockerRequirement_class.DOCKERREQUIREMENT);
        if (ExecutionConfiguration.getDockerRegistry().equals(this.executionUnit.getContainerUnit().getContainerRegistry())) {
            requirement.setDockerImageId(Optional.of(this.executionUnit.getContainerUnit().getContainerName()));
        } else {
            requirement.setDockerPull(Optional.of(this.executionUnit.getContainerUnit().getContainerRegistry() + "/" + this.executionUnit.getContainerUnit().getContainerName()));
        }
        return requirement;
    }

    private void createStepFromTask (ExecutionTask task) {
        String stepCWLFileId = this.executionUnit.getMetadata().get("name").toString().toLowerCase().replaceAll("[^a-zA-Z0-9]","")  + "_" + task.getWorkflowNodeId();
        TaoComponent wfComponent = TaskUtilities.getComponentFor(task);
        CommandLineTool commandLineTool = new CommandLineToolImpl();

        List<String> bashArguments;
        final boolean isBash = this.executionUnit.getArguments().get(0).equals("/bin/bash");
        if (isBash) {
            bashArguments = Arrays.asList(this.executionUnit.getArguments().get(2).split(" "));
        } else {
            bashArguments = this.executionUnit.getArguments();
        }

        List<Object> baseCommandArray = createCommandArray(bashArguments);
        List<Object> commandToolReqList = createReqListForCommandToolSteps();

        commandLineTool.setRequirements(Optional.of(commandToolReqList));
        commandLineTool.setBaseCommand(baseCommandArray);

        commandLineTool.setClass_(CommandLineTool_class.COMMANDLINETOOL);
        commandLineTool.setId(Optional.of(stepCWLFileId));

        cwlTemplate.getCwlMainGraphObject().get(CWL_GRAPH_ID).add(commandLineTool);

        // create task inputs based on Component sources
        addInputsComponentFromSources(wfComponent, bashArguments, stepCWLFileId, task);
        if (wfComponent instanceof ProcessingComponent && !((ProcessingComponent) wfComponent).getParameterDescriptors().isEmpty()) {
            addInputsFromParams(wfComponent, bashArguments, stepCWLFileId ,task);
        }
        addOutputsToWf(wfComponent, baseCommandArray, bashArguments, stepCWLFileId, task);
    }

    /**
     * Add input information to workflow template
     */
    private void addInputsComponentFromSources (TaoComponent wfComponent, List<String> bashArguments, String stepCWLFileId, ExecutionTask task) {
        Workflow cwlWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, cwlWorkflowId);
        // get current steps form Workflow
        Object currentStep = cwlWorkflow.getSteps().stream().filter(s->((WorkflowStep)s).getId().get().contains(wfComponent.getId().toLowerCase() + "_" + task.getWorkflowNodeId())).findFirst().orElse(null);
        for (SourceDescriptor sourceDescriptor : wfComponent.getSources()) {
            String inputId = sourceDescriptor.getName() + "_" + task.getWorkflowNodeId();
            CommandInputParameter input = new CommandInputParameterImpl();
            input.setId(Optional.of(inputId));
            Object associatedInput = null;

            if (currentStep != null) {
                Object currentStepIn = ((WorkflowStep) currentStep).getIn().stream().filter(in -> {
                    if (((WorkflowStepInput) in).getId().get().contains("#")) {
                        int chIdx = ((WorkflowStepInput) in).getId().get().indexOf("#");
                        return ((WorkflowStepInput) in).getId().get().substring(chIdx + 1).endsWith("/" + inputId);
                    } else {
                        return ((WorkflowStepInput) in).getId().get().equals(inputId);
                    }
                }).findFirst().orElse(null);
                String currentStepInVal;
                if (currentStepIn != null) {
                    if (((WorkflowStepInput) currentStepIn).getDefault() != null && ((WorkflowStepInput) currentStepIn).getDefault().isPresent()) {
                        currentStepInVal = ((WorkflowStepInput) currentStepIn).getDefault().get().toString();
                    } else if (((WorkflowStepInput) currentStepIn).getSource() != null) {
                        String tmpValue = ((WorkflowStepInput) currentStepIn).getSource().toString();
                        if (tmpValue.contains("#")) {
                            int chIdx = tmpValue.lastIndexOf("/");
                            currentStepInVal = tmpValue.substring(chIdx + 1);
                        } else {
                            currentStepInVal = tmpValue;
                        }
                    } else {
                        currentStepInVal = null;
                    }
                } else {
                    currentStepInVal = null;
                }

                associatedInput = cwlWorkflow.getInputs().stream().filter(in -> {
                    if (((WorkflowInputParameter) in).getId().get().contains("#")) {
                        return ((WorkflowInputParameter) in).getId().get().endsWith("/" + currentStepInVal);
                    } else {
                        return ((WorkflowInputParameter) in).getId().get().equals(currentStepInVal);
                    }
                }).findFirst().orElse(null);
            }
            Object type = associatedInput != null ? ((WorkflowInputParameter)associatedInput).getType() : Any.ANY.toString();
            input.setType(type);
            CommandLineBinding inputBinding = new CommandLineBindingImpl();
            // check if source name is found in bash arguments
            String foundPrefix = bashArguments.stream().filter(arg -> arg.matches("-?" + sourceDescriptor.getName() + "(=.*)?")).findFirst().orElse(null);
            if (foundPrefix != null) {
                int chIndex = foundPrefix.indexOf("=");
                // if source name is found containing equal sign, add only the first part; else add the found prefix
                if (chIndex > -1) {
                    inputBinding.setPrefix(Optional.of(foundPrefix.substring(0,chIndex + 1 )));
                } else {
                    inputBinding.setPrefix(Optional.of(foundPrefix));
                }
            } else {
                inputBinding.setPrefix(Optional.of("-" + sourceDescriptor.getName()));
            }
            inputBinding.setValueFrom("$(self.path)");
            input.setInputBinding(Optional.of(inputBinding));
            ((CommandLineTool)this.cwlTemplate.getCwlGraphChild("$graph", stepCWLFileId)).getInputs().add(input);
        }
    }

    /**
     * Add the parameters to job CWL file, inputs section and YAML file
     */
    private void addInputsFromParams (TaoComponent wfComponent, List<String> bashArguments, String stepCWLFileId, ExecutionTask task) {
        Workflow cwlWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, cwlWorkflowId);
        Workflow mainWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, MAIN_WF_ID);
        // get current steps form Workflow
        Object currentStep = cwlWorkflow.getSteps().stream().filter(s->((WorkflowStep)s).getId().get().contains(wfComponent.getId().toLowerCase() + "_" + task.getWorkflowNodeId())).findFirst().orElse(null);

        // get in element from current step
        List<Object> inList = ((WorkflowStep)currentStep).getIn();
        // main Workflow file has only 1 step. The one that calls the second Workflow file
        List<Object> mainWfInList = ((WorkflowStep)mainWorkflow.getSteps().getFirst()).getIn();

        for (ParameterDescriptor desc : ((ProcessingComponent)wfComponent).getParameterDescriptors()) {
            if (task.getInputParameterValues().stream().anyMatch(t -> t.getKey().equals(desc.getName()))) {
                // create parameter input for current execution unit cwl file and add it to inputs
                CommandInputParameter input = new CommandInputParameterImpl();
                input.setId(Optional.of(desc.getLabel()));
                input.setType(JavaType.fromClass(desc.getDataType()).friendlyName());
                input.setLabel(Optional.of(desc.getLabel()));
                CommandLineBinding inputBinding = new CommandLineBindingImpl();
                String foundPrefix = bashArguments.stream().filter(arg -> arg.matches("-?" + desc.getLabel() + "(=.*)?")).findFirst().orElse(null);
                if (foundPrefix != null) {
                    int chIndex = foundPrefix.indexOf("=");
                    if (chIndex > -1) {
                        inputBinding.setPrefix(Optional.of(foundPrefix.substring(0, chIndex + 1)));
                    } else {
                        inputBinding.setPrefix(Optional.of(foundPrefix));
                    }
                } else {
                    inputBinding.setPrefix(Optional.of("-" + desc.getLabel()));
                }
                input.setInputBinding(Optional.of(inputBinding));
                ((CommandLineTool) this.cwlTemplate.getCwlGraphChild("$graph", stepCWLFileId)).getInputs().add(input);

                // create parameter input for workflow and add it to inputs
                WorkflowInputParameter workflowInputParameter = new WorkflowInputParameterImpl();
                workflowInputParameter.setId(Optional.of(desc.getName() + "_" + task.getWorkflowNodeId()));
                workflowInputParameter.setType(JavaType.fromClass(desc.getDataType()).friendlyName());
                workflowInputParameter.setLabel(Optional.of(desc.getDescription()));
                cwlWorkflow.getInputs().add(workflowInputParameter);

                final String parameterId;
                String tempParameterId = MAIN_WF_INPUT_ID_PREFIX + desc.getName();
                long keyOccurrence = mainWorkflow.getInputs().stream().filter(m -> ((WorkflowInputParameter) m).getId().get().matches("^(file:/|file://|file:///).*") ?
                        ((WorkflowInputParameter) m).getId().get().matches("^(.*/)" + tempParameterId + ".*") :
                        ((WorkflowInputParameter) m).getId().get().matches("^" + tempParameterId + ".*")).count();
                if (keyOccurrence > 0) {
                    parameterId = tempParameterId + "_" + keyOccurrence;
                } else {
                    parameterId = tempParameterId;
                }
                if (mainWorkflow.getInputs().stream().filter(i ->
                        ((WorkflowInputParameter) i).getId().get().endsWith("/" + parameterId)).findAny().isEmpty()) {
                    WorkflowInputParameter mainWorkflowInputParameter = new WorkflowInputParameterImpl();
                    mainWorkflowInputParameter.setId(Optional.of(parameterId));
                    mainWorkflowInputParameter.setType(JavaType.fromClass(desc.getDataType()).friendlyName());
                    mainWorkflowInputParameter.setLabel(Optional.of(desc.getDescription()));
                    mainWorkflow.getInputs().add(mainWorkflowInputParameter);
                }

                // create input for step workflow add it to in list
                if (inList.stream().filter(i ->
                        ((WorkflowStepInput) i).getId().get().endsWith("/" + desc.getLabel())).findAny().isEmpty()) {
                    WorkflowStepInput stepInput = new WorkflowStepInputImpl();
                    stepInput.setId(Optional.of(desc.getLabel()));
                    stepInput.setDefault(Optional.of(desc.getName() + "_" + task.getWorkflowNodeId()));
                    inList.add(stepInput);

                    WorkflowStepInput mainStepInput = new WorkflowStepInputImpl();
                    mainStepInput.setId(Optional.of(desc.getName() + "_" + task.getWorkflowNodeId()));
                    mainStepInput.setDefault(Optional.of(parameterId));
                    mainWfInList.add(mainStepInput);
                }

                Variable parameterValue = task.getInputParameterValues().stream().filter(i -> i.getKey().equals(desc.getName())).findFirst().get();
                // create parameter input for YAML file
                createOrUpdateYamlFile(parameterId, JavaType.fromClass(desc.getDataType()).getConverter().apply(parameterValue.getValue()));
            }
        }
    }

    /**
     * Add output information to workflow template
     */
    private void addOutputsToWf (TaoComponent wfComponent, List<Object> baseCommandArray, List<String> bashArguments, String stepCWLFileId, ExecutionTask task) {
        // arguments associated with the outputs
        int argumentPosition = baseCommandArray.size();
        // create task outputs and arguments based on Component targets
        for (TargetDescriptor targetDescriptor : wfComponent.getTargets()) {
            argumentPosition++;
            CommandOutputParameter output = new CommandOutputParameterImpl();
            CommandLineBinding argumentOut = new CommandLineBindingImpl();

            String outputId = targetDescriptor.getName() + "_" + task.getWorkflowNodeId();

            output.setId(Optional.of(outputId));
            argumentOut.setValueFrom("outputs." + outputId);

            output.setType(getInOutType(targetDescriptor.getDataDescriptor().getFormatType(), targetDescriptor.getDataDescriptor().getLocation()));
            CommandOutputBinding outputBinding = new CommandOutputBindingImpl();
            String targetLocation = targetDescriptor.getDataDescriptor().getLocation();
            String targetExtension = targetLocation.substring(targetLocation.lastIndexOf("."));
            outputBinding.setGlob("*" + targetExtension);
            output.setOutputBinding(Optional.of(outputBinding));

            ((CommandLineTool)this.cwlTemplate.getCwlGraphChild("$graph", stepCWLFileId)).getOutputs().add(output);

            argumentOut.setPosition(argumentPosition);
            // set prefix
            // check if target name is found in bash arguments
            String foundPrefix = bashArguments.stream().filter(arg -> arg.matches("-" + targetDescriptor.getName() + "(=.*)?")).findFirst().orElse(null);
            if (foundPrefix != null) {
                int chIndex = foundPrefix.indexOf("=");
                // if source name is found containing equal sign, add only the first part; else add the found prefix
                if (chIndex > -1) {
                    argumentOut.setPrefix(Optional.of(foundPrefix.substring(0,chIndex + 1 )));
                } else {
                    argumentOut.setPrefix(Optional.of(foundPrefix));
                }
            } else {
                argumentOut.setPrefix(Optional.of("-" + targetDescriptor.getName()));
            }
            ((CommandLineTool)this.cwlTemplate.getCwlGraphChild("$graph", stepCWLFileId)).getArguments().get().add(argumentOut);
        }
        int bashArgsLastNonParamPosition = ListIterate.detectIndex(bashArguments, b -> b.startsWith("-") && !b.equals("-c"));
        for (int i = bashArgsLastNonParamPosition; i < bashArguments.size(); i++) {
            CommandLineBinding argumentOut = new CommandLineBindingImpl();
            if (bashArguments.get(i).startsWith("-")) {
                String argOutPrefix;
                String argOutValue;
                if (bashArguments.get(i).matches("-\\w+=(\\w|/|\\.)+")) {
                    String[] commandList = bashArguments.get(i).split("=");
                    argOutPrefix = commandList[0] + "=";
                    argumentOut.setPosition(argumentPosition);
                    argOutValue = commandList[1];
                } else {
                    argOutPrefix = bashArguments.get(i);
                    argOutValue = bashArguments.get(i + 1);
                    i++;
                }
                // arguments associated with the outputs
                List<Object> existingArguments = ((CommandLineTool)this.cwlTemplate.getCwlGraphChild("$graph", stepCWLFileId)).getArguments().get();
                List<Object> inputs = ((CommandLineTool)this.cwlTemplate.getCwlGraphChild("$graph", stepCWLFileId)).getInputs();
                boolean alreadyAdded = existingArguments.stream().anyMatch(a -> ((CommandLineBinding)a).getPrefix().get().equals(argOutPrefix)) ||
                        inputs.stream().anyMatch(in -> ((CommandInputParameter)in).getInputBinding().get().getPrefix().get().equals(argOutPrefix));
                if (!alreadyAdded) {
                    argumentPosition++;
                    argumentOut.setPosition(argumentPosition);
                    argumentOut.setPrefix(Optional.of(argOutPrefix));
                    argumentOut.setValueFrom(argOutValue);
                    ((CommandLineTool)this.cwlTemplate.getCwlGraphChild("$graph", stepCWLFileId)).getArguments().get().add(argumentOut);
                }
            }
        }
    }

    /**
     * Order workflow nodes based on orderNodes method and node level
     * @return list of workflow node descriptor
     */
    private List<WorkflowNodeDescriptor> orderWfDescNodes(WorkflowDescriptor workflowNodeDesc) {
        List<WorkflowNodeDescriptor> nodes = workflowNodeDesc.orderNodes(workflowNodeDesc.getNodes(),null);
        // order nodes by level too.
        return nodes.stream().sorted(Comparator.comparing(WorkflowNodeDescriptor::getLevel)).collect(Collectors.toList());
    }

    private String getInOutType(DataFormat formatType, String filePath) {
        final String retVal;
        switch (formatType) {
            case FOLDER:
                retVal = Directory_class.DIRECTORY.toString();
                break;
            case OTHER:
                if (filePath != null && (filePath.endsWith("/") || filePath.endsWith("\\"))) {
                    retVal = Directory_class.DIRECTORY.toString();
                } else {
                    retVal = Any.ANY.toString();
                }
                break;
            default:
                if (filePath != null && (filePath.endsWith("/") || filePath.endsWith("\\"))) {
                    retVal = Directory_class.DIRECTORY.toString();
                } else {
                    retVal = File_class.FILE.toString();
                }
        }
        return retVal;
    }

    private void writeToFile(Path path, Map<Object, Object> cwlTemplateObject) {
        Map<Object, Object> tempList = new LinkedHashMap<>();
        MyYAMLFactory factory = new MyYAMLFactory();
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        try {
            if (Files.exists(path)) {
                Map<?, ?> existingInputList = mapper.readValue(path.toFile(), Map.class);
                if (existingInputList != null && !existingInputList.isEmpty()) {
                    tempList.putAll(existingInputList);
                }
            }
            if (cwlTemplateObject != null && !cwlTemplateObject.isEmpty()) {
                tempList.putAll(cwlTemplateObject);
                mapper.writeValue(path.toFile(), tempList);
            }
        } catch (IOException e) {
            this.logger.severe(ExceptionUtils.getStackTrace(this.logger, e));
        }

    }
}
