package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.utils.RootLoader;
import ro.cs.tao.component.*;
import ro.cs.tao.datasource.DataSourceComponent;
import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.persistence.ExecutionTaskProvider;
import ro.cs.tao.execution.util.TaskUtilities;
import ro.cs.tao.persistence.WorkflowProvider;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.cwl.CWLTemplate;
import ro.cs.tao.utils.yaml.*;
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

import static ro.cs.tao.utils.cwl.CWLConstants.*;

public class CWLExportExecutor extends Executor<Object>
        implements ExecutionDescriptorConverter {

    private ExecutionUnit executionUnit;
    private final ExecutionTaskProvider taskProvider;
    private final WorkflowProvider workflowProvider;
    private final CWLTemplate cwlTemplate;
    private final Set<WorkflowNodeDescriptor> visitedNodes;

    private String cwlWorkflowId;
    private String mainWfStepId;

    public CWLExportExecutor() {
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.cwlTemplate = new CWLTemplate();
        this.visitedNodes = new HashSet<>();
    }

    public CWLExportExecutor(ExecutionUnit unit) {
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
            cwlTemplate.setCwlVersion(CWL_VERSION);
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

    private void createCWL(WorkflowDescriptor workflowDesc, ExecutionTask task, ExecutionUnit executionUnit) {
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

        mainWorkflow.setRequirements(Optional.of(new LinkedList<>(List.of(subWfFeatureReq))));
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

        cwlWorkflow.setRequirements(Optional.of(new LinkedList<>(List.of(multipleInputFeatureReq))));

        cwlTemplate.getCwlMainGraphObject().get(CWL_GRAPH_ID).add(mainWorkflow);
        cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).add(cwlWorkflow);

        parseNodes(workflowDesc);

        // Update root workflow step
        WorkflowStep mainWorkflowStep = mainWorkflow.getStep(mainWfStepId);

        // set run parameter for step found in main workflow
        mainWorkflowStep.setRun(cwlWorkflow.getId().get());

        createStepFromTask(task);

        writeToFile(path, cwlTemplate.save());
    }

    private void updateCWL(ExecutionTask task, ExecutionUnit executionUnit) {
        final Path path = Paths.get(executionUnit.getScriptTargetPath());
        Object graphElements = RootLoader.loadDocument(path);
        if (!(graphElements instanceof Collection<?>)) {
            throw new IllegalStateException("Loaded CWL graph is not a Collection: " + graphElements.getClass());
        }
        cwlTemplate.getCwlMainGraphObject().get(CWL_GRAPH_ID).addAll((Collection<?>) graphElements);
        createStepFromTask(task);
        writeToFile(path, cwlTemplate.save());
    }

    /**
     * Create or update the YAML file associated with the current job CWL file
     */
    private void createOrUpdateYamlFile(String key, Object yamlObj) {
        Map<Object, Object> tempList = new LinkedHashMap<>();

        final Path path = Paths.get(executionUnit.getScriptTargetPath());
        String yamlPathParent = path.getParent().toString();
        final Path yamlFile =  Paths.get(yamlPathParent + File.separator + YAML_FILE_NAME);

        CWLYAMLFactory factory =  new CWLYAMLFactory();
        factory.enable(CWLYAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(CWLYAMLGenerator.Feature.WRITE_DOC_START_MARKER);

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
                    String inputType = getInOutType(yamlObjTemp.getDataDescriptor().getFormatType(), yamlObjTemp.getDataDescriptor().getLocation(), true);
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

    private void parseNodes(WorkflowDescriptor workflowDesc) {
        List<WorkflowNodeDescriptor> orderedWfNodeDescList = orderWfAscNodes(workflowDesc);
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
        }
        for (WorkflowNodeDescriptor wf : orderedWfNodeDescList) {
            if (!wf.getIncomingLinks().isEmpty()) {
                describeWorkflowSteps(wf);
            }
        }
    }

    private void setupWorkflowInfoFromRootNode(TaoComponent wfComponent, SourceDescriptor sourceDescriptor, Long workflowId) {
        if (DataSourceComponent.QUERY_PARAMETER.equals(sourceDescriptor.getName())) {
            Workflow mainWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, MAIN_WF_ID);
            Workflow cwlWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, cwlWorkflowId);
            String inputType = getInOutType(sourceDescriptor.getDataDescriptor().getFormatType(), sourceDescriptor.getDataDescriptor().getLocation());
            String inputId = wfComponent.getTargets().getFirst().getName() + "_" + workflowId;

            WorkflowInputParameter mainWfInput = new WorkflowInputParameterImpl();
            mainWfInput.setId(Optional.of(inputId));
            mainWfInput.setType(inputType);

            mainWorkflow.getInputs().add(mainWfInput);

            WorkflowInputParameter cwlWfInput = new WorkflowInputParameterImpl();
            cwlWfInput.setType(inputType);
            cwlWfInput.setId(Optional.of(inputId));
            cwlWorkflow.getInputs().add(cwlWfInput);

            // add input to main workflow step
            WorkflowStepInput stepIn = new WorkflowStepInputImpl();
            stepIn.setId(Optional.of(inputId));
            stepIn.setDefault(Optional.of(inputId));
            mainWorkflow.getStep(mainWfStepId).getIn().add(stepIn);

            createOrUpdateYamlFile(inputId, sourceDescriptor);
        }
    }

    private void setupWorkflowInfoFromLeavesNode(TargetDescriptor targetDescriptor, Long workflowId) {
        Workflow mainWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, MAIN_WF_ID);
        Workflow cwlWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, cwlWorkflowId);
        String outParamId = targetDescriptor.getName() + "_" + workflowId;
        String outputType = getInOutType(targetDescriptor.getDataDescriptor().getFormatType(), targetDescriptor.getDataDescriptor().getLocation());
        String outputId = targetDescriptor.getParentId().toLowerCase() + "_" + outParamId;
        String outputSource = targetDescriptor.getParentId().toLowerCase() + "_" + workflowId + "/" + outParamId;

        String mainOutputSource = MAIN_WF_OUTPUT_SOURCE_PREFIX + cwlWorkflowId + "/" + outputId;

        WorkflowOutputParameter mainWfOutput = new WorkflowOutputParameterImpl();
        mainWfOutput.setId(Optional.of(outputId));
        mainWfOutput.setType(outputType);
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

    private void describeWorkflowSteps(WorkflowNodeDescriptor wf) {
        Workflow cwlWorkflow = (Workflow) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, cwlWorkflowId);
        WorkflowStep step = new WorkflowStepImpl();
        String stepId = wf.getComponentId().toLowerCase() + "_" + wf.getId();
        List<Object> inList = new ArrayList<>();
        List<Object> outList = new ArrayList<>();
        for (ComponentLink link : wf.getIncomingLinks()) {
            WorkflowStepInput in = new WorkflowStepInputImpl();
            in.setId(Optional.of(link.getOutput().getName() + "_" + wf.getId()));
            WorkflowNodeDescriptor linkedNode = visitedNodes.stream().filter(n -> n.getId().equals(link.getSourceNodeId())).findFirst().orElse(null);
            if (linkedNode == null) {
                System.out.println("test");
            }
            if (linkedNode != null && !linkedNode.getIncomingLinks().isEmpty()) {
                String linkedInput = linkedNode.getComponentId().toLowerCase() + "_" + linkedNode.getId();
                in.setDefault(Optional.of(linkedInput + "/" + link.getInput().getName() + "_" + link.getSourceNodeId()));
            } else {
                in.setDefault(Optional.of(link.getInput().getName() + "_" + link.getSourceNodeId()));
            }
            inList.add(in);
        }
        WorkflowNodeDescriptor wfLinkedNode = visitedNodes.stream().filter(n -> n.getId().equals(wf.getId())).findFirst().orElse(null);
        if (wfLinkedNode != null) {
            TaoComponent wfTaskComponent = TaskUtilities.getComponentFor(wfLinkedNode);
            for (TargetDescriptor targetDescriptor : wfTaskComponent.getTargets()) {
                WorkflowStepOutput out = new WorkflowStepOutputImpl();
                out.setId(Optional.of(targetDescriptor.getName() + "_" + wf.getId()));
                outList.add(out);
            }
        }
        step.setId(Optional.of(stepId));
        step.setRun(stepId);
        step.setIn(inList);
        step.setOut(outList);
        cwlWorkflow.getSteps().add(step);
    }

    private List<Object> createReqListForCommandToolSteps() {
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
        requirement.setDockerPull(Optional.of(this.executionUnit.getContainerUnit().getContainerRegistry() + "/" + this.executionUnit.getContainerUnit().getContainerName()));
        return requirement;
    }

    private void createStepFromTask(ExecutionTask task) {
        String stepCWLFileId = this.executionUnit.getMetadata().get("name").toString().toLowerCase().replaceAll("[^a-zA-Z0-9\\-]","")  + "_" + task.getWorkflowNodeId();
        TaoComponent wfComponent = TaskUtilities.getComponentFor(task);
        CommandLineTool commandLineTool = new CommandLineToolImpl();

        if (this.executionUnit.getArguments().stream().anyMatch(f -> f.equals("/bin/bash"))) {
            List<String> baseCommandArray = new ArrayList<>();
            for (String execUnitArg : this.executionUnit.getArguments()) {
                if (execUnitArg.equals("/bin/bash") || execUnitArg.equals("-c")) {
                    baseCommandArray.add(execUnitArg);
                } else {
                    if (execUnitArg.contains("cp")) {
                        logger.info("CP is needed");
                    }
                    if (execUnitArg.contains("rm -r")) {
                        logger.info("RM -R is needed");
                    }
                }
            }
            commandLineTool.setBaseCommand(baseCommandArray);
        } else {
            logger.info("Unhandled execution command for " + task.getWorkflowNodeId());
        }
        List<Object> commandToolReqList = createReqListForCommandToolSteps();

        commandLineTool.setRequirements(Optional.of(commandToolReqList));
        commandLineTool.setClass_(CommandLineTool_class.COMMANDLINETOOL);
        commandLineTool.setId(Optional.of(stepCWLFileId));

        cwlTemplate.getCwlMainGraphObject().get(CWL_GRAPH_ID).add(commandLineTool);

        addInputsFromTask(wfComponent, stepCWLFileId, task);
        addOutputsToWf(wfComponent, stepCWLFileId, task);
    }

    /**
     * Add output information to workflow template
     */
    private void addOutputsToWf(TaoComponent wfComponent, String stepCWLFileId, ExecutionTask task) {
        // create task outputs and arguments based on Component targets
        for (TargetDescriptor targetDescriptor : wfComponent.getTargets()) {
            CommandOutputParameter output = new CommandOutputParameterImpl();
            String outputId = targetDescriptor.getName() + "_" + task.getWorkflowNodeId();

            output.setId(Optional.of(outputId));
            output.setType(getInOutType(targetDescriptor.getDataDescriptor().getFormatType(), targetDescriptor.getDataDescriptor().getLocation()));
            CommandOutputBinding outputBinding = new CommandOutputBindingImpl();
            String targetName = targetDescriptor.getName();
            try {
                // arguments associated with the outputs
                Variable associatedInputParam = task.getInputParameterValues().stream().filter(t -> t.getKey().endsWith(targetName)).findFirst().get();
                String associatedInputId = associatedInputParam.getKey() + "_" + task.getWorkflowNodeId() + WF_OUTPUT_NAME_SUFFIX;
                outputBinding.setGlob("$(inputs." + associatedInputId + ")");
                output.setOutputBinding(Optional.of(outputBinding));
            } catch (Exception e) {
                logger.warning("Associated Input Parameter not found for workflow output: " + targetName);
            }
            ((CommandLineTool)this.cwlTemplate.getCwlGraphChild("$graph", stepCWLFileId)).getOutputs().add(output);
        }
    }

    private void addInputsFromTask(TaoComponent wfComponent, String stepCWLFileId, ExecutionTask task) {
        CommandLineTool commandLineToolWf = (CommandLineTool)cwlTemplate.getCwlGraphChild("$graph", stepCWLFileId);
        Workflow cwlWorkflow = (Workflow)cwlTemplate.getCwlGraphChild("$graph", cwlWorkflowId);
        Workflow mainWorkflow = (Workflow)cwlTemplate.getCwlGraphChild("$graph", MAIN_WF_ID);
        List<String> executionCommandArray = Arrays.asList(task.getCommand().split("\\R"));
        Object currentStep = cwlWorkflow.getStep(stepCWLFileId);
        for (Variable inputParameter : task.getInputParameterValues()) {
            String inputId = inputParameter.getKey() + "_" + task.getWorkflowNodeId();
            CommandInputParameter input = new CommandInputParameterImpl();
            int commandParamIndex = -1;
            SourceDescriptor parameterSourceDescriptor = wfComponent.getSources().stream().filter(s -> s.getName().equals(inputParameter.getKey())).findFirst().orElse(null);

            if (parameterSourceDescriptor != null) {
                Object inheritType; //default value
                commandParamIndex = getCommandParamIdx(executionCommandArray, parameterSourceDescriptor.getName());
                // in this case the input is already added in YAML file when Workflow is created
                input.setId(Optional.of(inputId));
                Object associatedElement = getAssociatedElement(currentStep, inputId, task.getWorkflowNodeId());
                if (associatedElement != null) {
                    inheritType = ((WorkflowInputParameter)associatedElement).getType();
                } else {
                    inheritType = getInOutType(parameterSourceDescriptor.getDataDescriptor().getFormatType(), inputParameter.getValue());
                    /*if (parameterSourceDescriptor.getCardinality() == 0) {
                        inheritType = inheritType + "[]";
                    }*/
                }
                input.setType(inheritType);
                if (commandParamIndex > -1) {
                    // use toString() for inheritType, because it can be string or class org.w3id.cwl.cwl1_2.InputArraySchemaImpl
                    if (!inputParameter.getValue().startsWith("[") && inheritType.toString().contains("[")) {
                        input.setType(inheritType.toString().replaceAll("\\[]", ""));
                        // in this case each input will be input to workflow
                        ScatterFeatureRequirement scatterFeatureReq = new ScatterFeatureRequirementImpl();
                        scatterFeatureReq.setClass_(ScatterFeatureRequirement_class.SCATTERFEATUREREQUIREMENT);
                        mainWorkflow.getRequirements().get().add(scatterFeatureReq);
                        WorkflowStep mainWorkflowStep = mainWorkflow.getStep(mainWfStepId);
                        if (associatedElement != null) {
                            mainWorkflowStep.setScatter(List.of(((WorkflowInputParameter) associatedElement).getId().get()));
                            mainWorkflowStep.setScatterMethod(Optional.of(ScatterMethod.DOTPRODUCT));
                        }

                        mainWorkflow.getOutputs().stream().filter(o -> ((WorkflowOutputParameter)o).getId().get().startsWith(wfComponent.getId()))
                                .forEach(out -> ((WorkflowOutputParameter) out).setType(((WorkflowOutputParameter) out).getType().toString()+"[]"));

                        cwlWorkflow.getInputs().stream().filter(i -> associatedElement!= null && ((WorkflowInputParameter)i).getId().get().equals(((WorkflowInputParameter) associatedElement).getId().get()))
                                        .forEach(in -> ((WorkflowInputParameter) in).setType(input.getType().toString()));
                        updateExecutionCommand(executionCommandArray, inputId, commandParamIndex, inheritType, inheritType, inputParameter.getValue());
                    } else {
                        updateExecutionCommand(executionCommandArray, inputId, commandParamIndex, inheritType, inheritType, inputParameter.getValue());
                    }
                } else {
                    updateExecutionCommand(executionCommandArray, inputId, inputParameter.getValue(), inheritType.toString(), false);
                }
            } else {
                TargetDescriptor parameterTargetDescriptor = wfComponent.getTargets().stream().filter(t -> t.getName().equals(inputParameter.getKey())).findFirst().orElse(null);
                boolean mandatory = true;
                if (parameterTargetDescriptor != null) {
                    commandParamIndex = getCommandParamIdx(executionCommandArray, parameterTargetDescriptor.getName());
                    inputId = inputId + WF_OUTPUT_NAME_SUFFIX;
                    input.setLabel(Optional.of("Output file for " + inputParameter.getKey() + " found in " + stepCWLFileId));
                } else {
                    ParameterDescriptor wfParamDesc = ((ProcessingComponent) wfComponent).getParameterDescriptors().stream().filter(p -> p.getName().equals(inputParameter.getKey())).findFirst().orElse(null);
                    if (wfParamDesc != null) {
                        if (!wfParamDesc.isNotNull()) {
                            mandatory = false;
                        }
                        if (wfParamDesc.getDescription() != null) {
                            input.setLabel(Optional.of(wfParamDesc.getDescription()));
                        }
                        commandParamIndex = getCommandParamIdx(executionCommandArray, wfParamDesc.getLabel());
                    }
                }
                input.setId(Optional.of(inputId));
                Class<?> inputClass = inputParameter.getValue().getClass();
                String inputType = JavaType.fromClass(inputClass).friendlyName();
                if (!mandatory) {
                    inputType = inputType + "?";
                }
                input.setType(inputType);

                cwlWorkflow.getInputs().add(input);
                mainWorkflow.getInputs().add(input);

                String ymlInputValue = inputParameter.getValue();
                if (inputParameter.getValue().contains("/")) {
                    ymlInputValue = ymlInputValue.substring(ymlInputValue.lastIndexOf("/") + 1);
                }
                createOrUpdateYamlFile(inputId, JavaType.fromClass(inputClass).parse(ymlInputValue));
                addInputWfToSteps(stepCWLFileId, input);
                if (commandParamIndex > -1) {
                    String regex = "[\\w/\\.-]+";
                    /*if (inputParameter.getValue().matches("[\\w/\\.-]+")) {
                        executionCommandArray.set(commandParamIndex + 1, "$(inputs." + inputId + ")");
                    } else {
                        executionCommandArray.set(commandParamIndex + 1, "\"$(inputs." + inputId + ")\"");
                    }*/
                    updateExecutionCommand(executionCommandArray, inputId, commandParamIndex, regex, inputParameter.getValue());
                } else {
                    updateExecutionCommand(executionCommandArray, inputId, inputParameter.getValue(), inputType, true);
                }
            }
            commandLineToolWf.getInputs().add(input);
        }
        // add parameters without value. Tose are not found in task parameters
        Set<ParameterDescriptor> wfParamDescList = ((ProcessingComponent)wfComponent).getParameterDescriptors();
        boolean mandatory;
        for (ParameterDescriptor wfParamDesc : wfParamDescList) {
            String inputId = wfParamDesc.getId() + "_" + task.getWorkflowNodeId();
            CommandInputParameter input = new CommandInputParameterImpl();
            mandatory = true;
            if (!wfParamDesc.isNotNull()) {
                mandatory = false;
            }
            if (wfParamDesc.getDescription() != null && !wfParamDesc.getDescription().isEmpty()) {
                input.setLabel(Optional.of(wfParamDesc.getDescription()));
            }
            input.setId(Optional.of(inputId));
            Class<?> inputClass = wfParamDesc.getDataType();
            String inputType = JavaType.fromClass(inputClass).friendlyName();
            if (inputType.equals("path")) {
                inputType = "File";
            }
            if (!mandatory) {
                inputType = inputType + "?";
            }
            input.setType(inputType);
            boolean alreadyAdded = commandLineToolWf.getInputs().stream().anyMatch(p -> ((CommandInputParameter)p).getId() != null
                    && ((CommandInputParameter)p).getId().isPresent() && ((CommandInputParameter)p).getId().get().equals(inputId));
            if (!alreadyAdded) {
                commandLineToolWf.getInputs().add(input);
            }
        }
        CommandLineBinding taskArguments = new CommandLineBindingImpl();
        taskArguments.setValueFrom(String.join(" \\\n", executionCommandArray));
        commandLineToolWf.getArguments().get().add(taskArguments);
    }

    private int getCommandParamIdx(List<String> executionCommandArray, String parameterName) {
        for(int i = 0; i < executionCommandArray.size(); i++) {
            if (executionCommandArray.get(i).contains("=")) {
                if(executionCommandArray.get(i).matches("-+" + Pattern.quote(parameterName) + "\\s*=.*")) {
                    return i;
                }
            } else if (executionCommandArray.get(i).equals("-" + parameterName)){
                return i;
            }
        }
        return -1;
    }

    private void updateExecutionCommand(List<String> executionCommandArray, String inputId, int commandParamIndex, Object inheritType, Object inputType, String regex, String inputParameterValue) {
        String tmpCommandPrefix = "";
        int tmpCommandParamIdx = commandParamIndex + 1;
        if (executionCommandArray.get(commandParamIndex).contains("=")) {
            tmpCommandParamIdx = commandParamIndex;
            int charIdx = executionCommandArray.get(commandParamIndex).indexOf("=");
            tmpCommandPrefix = executionCommandArray.get(commandParamIndex).substring(0, charIdx + 1);
        }
        if (inheritType != null) {
            if (inputParameterValue != null) {
                if (inputParameterValue.startsWith("[")) {
                    String[] paramValues = inputParameterValue.replaceAll("[\\[\\]\"]", "").split("\\s?,\\s?");
                    int paramNumber = paramValues.length;
                    for (int idx = 0; idx < paramNumber; idx++) {
                        if (inputType.toString().equals("File[]") && inheritType.toString().equals("Directory[]")) {
                            String fileName = paramValues[idx].substring(paramValues[idx].lastIndexOf("/"));
                            paramValues[idx] = "$(inputs." + inputId + "[" + idx + "].path)" + fileName;
                        } else {
                            paramValues[idx] = "$(inputs." + inputId + "[" + idx + "].path)";
                        }
                    }
                    executionCommandArray.set(tmpCommandParamIdx, tmpCommandPrefix + String.join(" ", paramValues));
                } else {
                    if (inputType.toString().equals("File") && inheritType.toString().contains("Directory")) {
                        String fileName = inputParameterValue.substring(inputParameterValue.lastIndexOf("/"));
                        executionCommandArray.set(tmpCommandParamIdx, tmpCommandPrefix + "$(inputs." + inputId + ".path)" + fileName);
                    } else {
                        executionCommandArray.set(tmpCommandParamIdx, tmpCommandPrefix + "$(inputs." + inputId + ".path)");
                    }
                }
            } else if (inheritType.toString().equals("File") || inheritType.toString().equals("Directory")) {
                executionCommandArray.set(tmpCommandParamIdx, tmpCommandPrefix + "$(inputs." + inputId + ".path)");
            } else {
                executionCommandArray.set(tmpCommandParamIdx, tmpCommandPrefix + "$(inputs." + inputId + ")");
            }
        }
        if (regex != null && inputParameterValue != null) {
            if (inputParameterValue.matches(regex)) {
                executionCommandArray.set(tmpCommandParamIdx, tmpCommandPrefix + "$(inputs." + inputId + ")");
            } else {
                executionCommandArray.set(tmpCommandParamIdx, tmpCommandPrefix + "\"$(inputs." + inputId + ")\"");
            }
        }
    }

    private void updateExecutionCommand(List<String> executionCommandArray, String inputId, int commandParamIndex, Object inheritType, Object inputType, String inputParameterValue) {
        updateExecutionCommand(executionCommandArray, inputId, commandParamIndex, inheritType, inputType, null, inputParameterValue);
    }

    private void updateExecutionCommand(List<String> executionCommandArray, String inputId, int commandParamIndex, String regex, String inputParameterValue) {
        updateExecutionCommand(executionCommandArray, inputId, commandParamIndex, null, null, regex, inputParameterValue);
    }

    private void updateExecutionCommand(List<String> executionCommandArray, String inputId, String inputParameterValue, String inputType, boolean isOutput) {
        for (int idx = 0; idx < executionCommandArray.size(); idx++) {
            if ((inputType.contains("[]") && inputParameterValue.contains(executionCommandArray.get(idx)))
                    || executionCommandArray.get(idx).equals(inputParameterValue)) {
                if (isOutput) {
                    executionCommandArray.set(idx, "$(inputs." + inputId + ")");
                } else {
                    executionCommandArray.set(idx, "$(inputs." + inputId + ".path)");
                }
            }
        }
    }

    private void addInputWfToSteps(String stepId, CommandInputParameter input) {
        Workflow cwlWorkflow = (Workflow)cwlTemplate.getCwlGraphChild("$graph", cwlWorkflowId);
        Workflow mainWorkflow = (Workflow)cwlTemplate.getCwlGraphChild("$graph", MAIN_WF_ID);

        WorkflowStepInput stepInput = new WorkflowStepInputImpl();
        stepInput.setId(input.getId());
        stepInput.setDefault(Optional.of(input.getId().get()));

        WorkflowStep mainWfCurrentStep = mainWorkflow.getStep(mainWfStepId);
        mainWfCurrentStep.getIn().add(stepInput);

        WorkflowStep cwlWfCurrentStep = cwlWorkflow.getStep(stepId);
        cwlWfCurrentStep.getIn().add(stepInput);
    }

    private Object getAssociatedElement(Object currentStep, String inputId, Long wfNodeId) {
        if (currentStep != null) {
            Workflow cwlWorkflow = (Workflow) this.cwlTemplate.getCwlGraphChild("$graph", this.cwlWorkflowId);
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
                        int chIdx = tmpValue.indexOf('/', tmpValue.indexOf('#'));
                        currentStepInVal = tmpValue.substring(chIdx + 1);
                    } else {
                        currentStepInVal = tmpValue;
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
            return cwlWorkflow.getInputs().stream().filter(in -> {
                if (((WorkflowInputParameter) in).getId().get().contains("#")) {
                    return ((WorkflowInputParameter) in).getId().get().endsWith("/" + currentStepInVal);
                } else {
                    return ((WorkflowInputParameter) in).getId().get().equals(currentStepInVal);
                }
            }).findFirst().orElse(null);
        }
        return null;
    }

    /**
     * Order workflow nodes ascendant based on orderNodes method and node level
     * @return list of workflow node descriptor
     */
    private List<WorkflowNodeDescriptor> orderWfAscNodes(WorkflowDescriptor workflowNodeDesc) {
        List<WorkflowNodeDescriptor> nodes = workflowNodeDesc.orderNodes(workflowNodeDesc.getNodes(),null);
        // order nodes by level too.
        return nodes.stream().sorted(Comparator.comparing(WorkflowNodeDescriptor::getLevel)).collect(Collectors.toList());
    }

    private String getInOutType(DataFormat formatType, String filePath) {
        return getInOutType(formatType, filePath, false);
    }

    private String getInOutType(DataFormat formatType, String filePath, boolean forYAML) {
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
        if (!forYAML && filePath != null && filePath.matches(".*?/?\\s?,\\s?/?.*")) {
            return retVal + "[]";
        }
        return retVal;
    }

    private void writeToFile(Path path, Map<Object, Object> cwlTemplateObject) {
        Map<Object, Object> tempList = new LinkedHashMap<>();
        CWLYAMLFactory factory = new CWLYAMLFactory();
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
