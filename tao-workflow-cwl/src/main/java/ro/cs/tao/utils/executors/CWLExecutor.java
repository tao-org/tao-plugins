package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.gs.collections.impl.utility.ListIterate;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.utils.RootLoader;
import org.w3id.cwl.cwl1_2.utils.Saveable;
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
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class CWLExecutor extends Executor<Object>
        implements ExecutionDescriptorConverter {
    private ExecutionUnit unit;
    private final ExecutionTaskProvider taskProvider;
    private final WorkflowProvider workflowProvider;
    private final WorkflowNodeProvider workflowNodeProvider;


    private final static String YAML_FILE_NAME = "inputs.yaml";
    private final static String MAIN_WF_INPUT_ID_PREFIX = "wf_in_";
    private final static String MAIN_WF_OUTPUT_ID_PREFIX = "wf_out_";
    private final static String MAIN_WF_OUTPUT_SOURCE_PREFIX = "workflow_";
    private final static String MAIN_WF_ID = "workflow_start";

    public CWLExecutor() {
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.workflowNodeProvider = SpringContextBridge.services().getService(WorkflowNodeProvider.class);
    }

    public CWLExecutor(ExecutionUnit unit) {
        super(unit.getHost(), unit.getArguments(), unit.asSuperUser());
        this.unit = unit;
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
        this.workflowNodeProvider = SpringContextBridge.services().getService(WorkflowNodeProvider.class);
    }

    @Override
    public boolean isIntendedFor(ExecutionUnitFormat unitFormat) {
        return unitFormat == ExecutionUnitFormat.CWL;
    }

    @Override
    public boolean canConnect() {
        return true;
    }

    @Override
    public int execute(boolean logMessages) throws Exception {
        if (this.unit == null) {
            throw new NullPointerException("Execution unit not set");
        }
        if (this.unit.getScriptTargetPath() != null) {
            long taskId = (Long) unit.getMetadata().get("id");
            ExecutionTask task = this.taskProvider.get(taskId);
            try {
                final Path path = Paths.get(unit.getScriptTargetPath());
                long workflowId = task.getJob().getWorkflowId();
                WorkflowDescriptor workflowDesc = this.workflowProvider.get(workflowId);

                boolean createFile = false;
                if (!Files.exists(path)) {
                    createFile = true;
                } else {
                    WorkflowNodeDescriptor node = this.workflowNodeProvider.get(task.getWorkflowNodeId());
                    List<WorkflowNodeDescriptor> orderedNodes = orderWfDescNodes(workflowDesc);
                    int nodePosition = orderedNodes.indexOf(node);
                    if (nodePosition > 0 && orderedNodes.get(nodePosition - 1).getIncomingLinks().size() > 0) {
                        createFile = false;
                    } else {
                        createFile = true;
                    }
                }
                Map<Object, Object> cwlMainObject = new LinkedHashMap<>();
                cwlMainObject.put("cwlVersion", CWLVersion.V1_2.toString());
                if (createFile) {
                    List<Object> cwlGraphList = new LinkedList<>();
                    System.out.println("Create main workflow");
                    final Workflow mainWorkflow = new WorkflowImpl();
                    mainWorkflow.setClass_(Workflow_class.WORKFLOW);
                    //mainWorkflow.setCwlVersion(Optional.of(CWLVersion.V1_2));
                    mainWorkflow.setId(Optional.of(MAIN_WF_ID));
                    mainWorkflow.setSteps(new ArrayList<>());

                    WorkflowStep mainWorkflowStep = new WorkflowStepImpl();
                    mainWorkflowStep.setIn(new ArrayList<>());
                    mainWorkflowStep.setOut(new ArrayList<>());

                    // add requirements to main Workflow file
                    List<Object> mainWorkflowReqList = new LinkedList<>();
                    SubworkflowFeatureRequirement subWfFeatureReq = new SubworkflowFeatureRequirementImpl();
                    subWfFeatureReq.setClass_(SubworkflowFeatureRequirement_class.SUBWORKFLOWFEATUREREQUIREMENT);
                    mainWorkflowReqList.add(subWfFeatureReq);

                    ScatterFeatureRequirement scatterFeatureReq = new ScatterFeatureRequirementImpl();
                    scatterFeatureReq.setClass_(ScatterFeatureRequirement_class.SCATTERFEATUREREQUIREMENT);
                    mainWorkflowReqList.add(scatterFeatureReq);

                    mainWorkflow.setRequirements(Optional.of(mainWorkflowReqList));

                    System.out.println("Create second workflow");
                    String cwlWorkflowId = path.getFileName().toString().substring(0,path.getFileName().toString().indexOf("."));
                    final Workflow cwlWorkflow = new WorkflowImpl();
                    cwlWorkflow.setClass_(Workflow_class.WORKFLOW);
                    cwlWorkflow.setId(Optional.of(cwlWorkflowId));

                    // add requirements to second Workflow file
                    List<Object> workflowReqList = new LinkedList<>();

                    /*
                     * ScatterFeatureRequirement is found in both Workflow files.
                     * Therefore is created only onece fon the main Workflow file and added to this too
                     */
                    workflowReqList.add(scatterFeatureReq);

                    MultipleInputFeatureRequirement multipleInputFeatureReq = new MultipleInputFeatureRequirementImpl();
                    multipleInputFeatureReq.setClass_(MultipleInputFeatureRequirement_class.MULTIPLEINPUTFEATUREREQUIREMENT);
                    workflowReqList.add(multipleInputFeatureReq);

                    cwlWorkflow.setRequirements(Optional.of(workflowReqList));

                    // create general inputs and outputs for both Workflow files
                    List<Object> inputList = new ArrayList<>();
                    List<Object> mainWfInputList = new ArrayList<>();
                    List<Object> outputList = new ArrayList<>();
                    List<Object> mainWfOutputList = new ArrayList<>();
                    Map<Object, Object> yamlFileList = new HashMap<>();

                    List<WorkflowNodeDescriptor> roots = workflowDesc.findRoots(workflowDesc.getNodes());
                    List<WorkflowNodeDescriptor> leaves = workflowDesc.findLeaves(workflowDesc.getNodes());

                    /** create the general inputs from workflow roots
                     * will be added main CWL file and to YML file
                     */
                    for (WorkflowNodeDescriptor wf  : roots) {
                        TaoComponent wfComponent = TaskUtilities.getComponentFor(wf);
                        for (SourceDescriptor source : wfComponent.getSources()) {
                            if (DataSourceComponent.QUERY_PARAMETER.equals(source.getName())) {
                                String inputType = getInOutType(source.getDataDescriptor().getFormatType(), source.getDataDescriptor().getLocation());
                                String mainWfInputType = inputType + "[]";
                                String inputId = wfComponent.getTargets().get(0).getName() + "_" + wf.getId();
                                String mainWfInputId = MAIN_WF_INPUT_ID_PREFIX + inputId;

                                WorkflowInputParameter mainWfInput = new WorkflowInputParameterImpl();
                                mainWfInput.setType(mainWfInputType);
                                mainWfInput.setId(Optional.of(mainWfInputId));
                                mainWfInputList.add(mainWfInput);

                                WorkflowInputParameter input = new WorkflowInputParameterImpl();
                                input.setType(inputType);
                                input.setId(Optional.of(inputId));
                                inputList.add(input);

                                // add input to main workflow step
                                WorkflowStepInput stepIn = new WorkflowStepInputImpl();
                                stepIn.setId(Optional.of(inputId));
                                stepIn.setDefault(mainWfInputId);
                                mainWorkflowStep.getIn().add(stepIn);

                                // add information to YAML file
                                List<Object> inputYamlFileList = new LinkedList<>();
                                if (source.getDataDescriptor().getLocation().contains(",")) {
                                    String[] strings = source.getDataDescriptor().getLocation().split(",");
                                    for (String sourceStr : strings) {
                                        Map<Object, Object> yamlFileObj = new HashMap<>();
                                        yamlFileObj.put("class", inputType);
                                        yamlFileObj.put("path", FileUtilities.toUnixPath(sourceStr));
                                        inputYamlFileList.add(yamlFileObj);
                                    }
                                } else {
                                    Map<Object, Object> yamlFileObj = new HashMap<>();
                                    yamlFileObj.put("class", inputType);
                                    yamlFileObj.put("path", FileUtilities.toUnixPath(source.getDataDescriptor().getLocation()));
                                    inputYamlFileList.add(yamlFileObj);
                                }
                                yamlFileList.put(mainWfInputId, inputYamlFileList);
                            }
                        }
                    }
                    mainWorkflow.setInputs(mainWfInputList);
                    cwlWorkflow.setInputs(inputList);
                    createOrUpdateYamlFile(path, yamlFileList);

                    /** create the general outputs from workflow leaves
                     * will be added main CWL file
                     */
                    for (WorkflowNodeDescriptor wf : leaves) {
                        TaoComponent leavesTaskComponent = TaskUtilities.getComponentFor(wf);
                        for(TargetDescriptor target : leavesTaskComponent.getTargets()) {
                            String outParamId = target.getName() + "_" + wf.getId() ;
                            String outputType = getInOutType(target.getDataDescriptor().getFormatType(), target.getDataDescriptor().getLocation());
                            String mainWfOutputType = outputType + "[]";
                            String outputId = target.getParentId().toLowerCase() + "_" + outParamId;
                            String mainWfOutputId = MAIN_WF_OUTPUT_ID_PREFIX + outputId;
                            String outputSource = target.getParentId().toLowerCase() + "_" + wf.getId() + "/" + outParamId;
                            String mainOutputSource = MAIN_WF_OUTPUT_SOURCE_PREFIX + cwlWorkflowId + "/" + outputId;

                            WorkflowOutputParameter mainWfOutput = new WorkflowOutputParameterImpl();
                            mainWfOutput.setId(Optional.of(mainWfOutputId));
                            mainWfOutput.setType(mainWfOutputType);
                            mainWfOutput.setOutputSource(mainOutputSource);
                            mainWfOutputList.add(mainWfOutput);

                            WorkflowOutputParameter output = new WorkflowOutputParameterImpl();
                            output.setId(Optional.of(outputId));
                            output.setType(outputType);
                            output.setOutputSource(outputSource);
                            outputList.add(output);

                            // add input to main workflow step
                            WorkflowStepOutput stepOut = new WorkflowStepOutputImpl();
                            stepOut.setId(Optional.of(outputId));
                            mainWorkflowStep.setId(Optional.of(MAIN_WF_OUTPUT_SOURCE_PREFIX + cwlWorkflowId));
                            mainWorkflowStep.getOut().add(stepOut);
                        }
                    }

                    // set scatter and scatterMethod for step found in main workflow
                    mainWorkflowStep.setScatter(mainWorkflowStep.getIn().stream().map(s -> ((WorkflowStepInput)s).getId().get()).collect(Collectors.toList()));
                    mainWorkflowStep.setScatterMethod(Optional.of(ScatterMethod.DOTPRODUCT));

                    // set run parameter for step found in main workflow
                    mainWorkflowStep.setRun(cwlWorkflow.getId().get());

                    mainWorkflow.getSteps().add(mainWorkflowStep);

                    mainWorkflow.setOutputs(mainWfOutputList);
                    cwlWorkflow.setOutputs(outputList);

                    List<Object> steps = createWorkflowCWLSteps(workflowDesc);
                    cwlWorkflow.setSteps(steps);

                    // add the CWL workflows
                    cwlGraphList.add(mainWorkflow.save());
                    cwlGraphList.add(cwlWorkflow.save());

                    // create the CWL file for the current workflow step
                    createStepCWL(cwlGraphList, path, workflowDesc, task, mainWorkflow, cwlWorkflow);

                    cwlMainObject.put("$graph", cwlGraphList);
                } else {
                    System.out.println("Update file");
                    String cwlWorkflowId = path.getFileName().toString().substring(0,path.getFileName().toString().indexOf("."));
                    List<Object> graphElements = List.class.cast(RootLoader.loadDocument(path));
                    List<Object> definedWorkflows = graphElements.stream().filter(el -> el instanceof Workflow).collect(Collectors.toList());

                    final Workflow mainExistingCWL = (Workflow) definedWorkflows.stream().filter(wf -> ((Workflow)wf).getId().get().matches("(.*)?" + MAIN_WF_ID + "$")).findFirst().get();
                    final Workflow existingCWL = (Workflow) definedWorkflows.stream().filter(wf -> ((Workflow)wf).getId().get().matches("(.*)?" + cwlWorkflowId + "$")).findFirst().get();

                    List<Object> prettifiedGraphElements = graphElements.stream().map(el ->{ return ((Saveable)el).save();}).collect(Collectors.toList());

                    createStepCWL(prettifiedGraphElements, path, workflowDesc, task, mainExistingCWL, existingCWL);
                    cwlMainObject.put("$graph", prettifiedGraphElements);
                }
                writeToFile(path, cwlMainObject);
            } catch (Exception e) {
                logger.severe(ExceptionUtils.getStackTrace(logger, e));
            }
        }
        return 0;
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

    /**
     * Create the step map found in the job CWL file,based on WorkflowNodeDescriptor list
     * @param workflowNodeDesc
     * @return list of objects
     */
    private List<Object> createWorkflowCWLSteps(WorkflowDescriptor workflowNodeDesc) {
        List<Object> steps = new ArrayList<>();
        Map<Long, Map<String, Object>> stepInfoMap = createStepsMap(workflowNodeDesc);
        List<WorkflowNodeDescriptor> orderedWfNodeDescList = orderWfDescNodes(workflowNodeDesc);

        for (WorkflowNodeDescriptor wf : orderedWfNodeDescList) {
            if (wf.getIncomingLinks().size() > 0) {
                WorkflowStep step = new WorkflowStepImpl();
                String stepId = wf.getComponentId().toLowerCase() + "_" + wf.getId();
                List<Object> inList = new ArrayList<>();
                List<Object> outList = new ArrayList<>();
                for (ComponentLink link : wf.getIncomingLinks()) {
                    WorkflowStepInput in = new WorkflowStepInputImpl();
                    in.setId(Optional.of(link.getOutput().getName() + "_" + wf.getId()));
                    if (((HashMap<?, ?>)stepInfoMap.get(link.getSourceNodeId()).get("in")).size() > 0) {
                        String linkedInput = stepInfoMap.get(link.getSourceNodeId()).get("name").toString();
                        in.setDefault(linkedInput + "/" + link.getInput().getName() + "_" + link.getSourceNodeId());
                    } else {
                        in.setDefault(link.getInput().getName() + "_" + link.getSourceNodeId());
                    }
                    inList.add(in);
                }
                for (Object output : (ArrayList) stepInfoMap.get(wf.getId()).get("out")) {
                    WorkflowStepOutput out = new WorkflowStepOutputImpl();
                    out.setId(Optional.of(output.toString()));
                    outList.add(out);
                }
                step.setId(Optional.of(stepId));
                step.setRun(wf.getName().toLowerCase() + "_" + wf.getId());
                step.setIn(inList);
                step.setOut(outList);
                steps.add(step);
            }
        }
        return steps;
    }

    /**
     * Creates map of maps, where the first key is the workflow node descriptor id.
     * The second key represents the parameter (in, out ot name) and the value is an object.
     * This map is used to associate the steps inputs and outputs in the job CWL file
     * @param workflowDescriptor
     */
    private Map<Long, Map<String, Object>> createStepsMap (WorkflowDescriptor workflowDescriptor) {
        Map<Long, Map<String, Object>> stepInfoMap = new HashMap<>();
        for (WorkflowNodeDescriptor wf : workflowDescriptor.getNodes()) {
            Long id = wf.getId();
            Map<String, Object> obj = new HashMap<>();
            TaoComponent wfTaskComponent = TaskUtilities.getComponentFor(wf);
            Map<Long, String> in = new HashMap<>();
            for (ComponentLink link : wf.getIncomingLinks()) {
                in.put(link.getSourceNodeId(),link.getOutput().getName());
            }
            obj.put("in", in);
            List<String> out = new ArrayList<>();
            for (TargetDescriptor target : wfTaskComponent.getTargets()) {
                out.add(target.getName() + "_" + wf.getId());
            }
            obj.put("out", out);
            obj.put("name", wf.getComponentId().toLowerCase() + "_" + wf.getId());
            stepInfoMap.put(id, obj);
        }
        return stepInfoMap;
    }

    /**
     * Create or update the YAML file assosciated with the current job CWL file
     * @param path
     * @param inputList
     */
    private void createOrUpdateYamlFile(Path path, Map<Object, Object> inputList) {
        Map<Object, Object> tempList = new LinkedHashMap<>();

        String yamlPathParent = path.getParent().toString();
        final Path yamlFile =  Paths.get(yamlPathParent + File.separator + YAML_FILE_NAME);

        MyYAMLFactory factory =  new MyYAMLFactory();
        factory.enable(MyYAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(MyYAMLGenerator.Feature.WRITE_DOC_START_MARKER);

        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        try {
            if (Files.exists(yamlFile)) {
                Map<Object, Object> existingInputList = mapper.readValue(yamlFile.toFile(), Map.class);
                if (existingInputList != null && existingInputList.size() > 0) {
                    tempList.putAll(existingInputList);
                }
            }
            if (inputList != null && inputList.size() > 0) {
                tempList.putAll(inputList);
                mapper.writeValue(yamlFile.toFile(), tempList);
            }
        } catch (IOException e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
    }

    /**
     * Creates the CWL file for each task
     * @param path
     * @param workflowDesc
     * @param task
     * @param existingWorkflow
     */
    private void createStepCWL(List<Object> cwlGraphList, Path path, WorkflowDescriptor workflowDesc, ExecutionTask task, Workflow mainExistingWorkflow, Workflow existingWorkflow) {
        String stepCWLFileId = this.unit.getMetadata().get("name").toString().toLowerCase() + "_" + task.getWorkflowNodeId();
        TaoComponent component = TaskUtilities.getComponentFor(task);
        CommandLineTool commandLineTool = new CommandLineToolImpl();

        commandLineTool.setClass_(CommandLineTool_class.COMMANDLINETOOL);
        //commandLineTool.setCwlVersion(Optional.of(CWLVersion.V1_2));
        commandLineTool.setId(Optional.of(stepCWLFileId));

        List<String> bashArguments;
        final boolean isBash = this.unit.getArguments().get(0).equals("/bin/bash");
        if (isBash) {
            bashArguments = Arrays.asList(this.unit.getArguments().get(2).split(" "));
        } else {
            bashArguments = this.unit.getArguments();
        }

        List<Object> baseCommandArray = new ArrayList();
        // first bash argument is always added
        baseCommandArray.add("/bin/bash");
        baseCommandArray.add(bashArguments.get(0).replaceAll("\"", ""));
        int bashArgsLastNonParamPosition = ListIterate.detectIndex(bashArguments, b -> b.toString().startsWith("-") && !b.toString().equals("-c"));
        if (bashArguments.get(1).startsWith(" \\")) {
            baseCommandArray.add(bashArguments.get(1).replaceAll("\"","").replaceAll("\\\\","/"));
        } else if (bashArguments.get(1).equals("-c")) {
            for(int i = 1 ; i < bashArgsLastNonParamPosition ; i++) {
                baseCommandArray.add(bashArguments.get(i));
            }
        }

        List<Object> requirementsList = new ArrayList<>();
        if (this.unit.getMinDisk() != null || this.unit.getMinMemory() != null) {
            ResourceRequirement requirement = new ResourceRequirementImpl();
            requirement.setClass_(ResourceRequirement_class.RESOURCEREQUIREMENT);
            requirement.setRamMin(this.unit.getMinMemory() != null ? this.unit.getMinMemory() : this.unit.getMinDisk());
            requirementsList.add(requirement);
        }
        if (this.unit.getContainerUnit() != null) {
            DockerRequirement requirement = new DockerRequirementImpl();
            requirement.setClass_(DockerRequirement_class.DOCKERREQUIREMENT);
            if (ExecutionConfiguration.getDockerRegistry().equals(this.unit.getContainerUnit().getContainerRegistry())) {
                //requirement.setDockerImageId(Optional.of(this.unit.getContainerUnit().getContainerRegistry() + "/" + this.unit.getContainerUnit().getContainerName()));
                requirement.setDockerImageId(Optional.of(this.unit.getContainerUnit().getContainerName()));
            } else {
                requirement.setDockerPull(Optional.of(this.unit.getContainerUnit().getContainerRegistry() + "/" + this.unit.getContainerUnit().getContainerName()));
            }
            requirementsList.add(requirement);
        }
        commandLineTool.setRequirements(Optional.of(requirementsList));

        commandLineTool.setBaseCommand(baseCommandArray);

        List<Object> inputs = new ArrayList<>();
        List<Object> outputs = new ArrayList<>();

        // get current steps form Workflow
       Object currentStep = existingWorkflow.getSteps().stream().filter(s->((WorkflowStep)s).getId().get().contains(component.getId().toLowerCase() + "_" + task.getWorkflowNodeId())).findFirst().orElse(null);

        // get inputs
        List<Object> mainWfExistingCWLInputs = mainExistingWorkflow.getInputs();
        List<Object> existingCWLInputs = existingWorkflow.getInputs();

        // create task inputs based on Component sources
        for (SourceDescriptor source : component.getSources()) {
            String inputId = source.getName() + "_" + task.getWorkflowNodeId();
            CommandInputParameter input = new CommandInputParameterImpl();
            input.setId(Optional.of(inputId));
            Object associatedInput = null;
            if (currentStep != null) {
                Object currentStepIn = ((WorkflowStep) currentStep).getIn().stream().filter(in -> {
                    if (((WorkflowStepInput) in).getId().get().contains("#")) {
                        int chIdx = ((WorkflowStepInput) in).getId().get().indexOf("#");
                        return ((WorkflowStepInput) in).getId().get().substring(chIdx + 1).endsWith("/" + inputId);
                    }
                    else {
                        return  ((WorkflowStepInput) in).getId().get().equals(inputId);
                    }
                }).findFirst().orElse(null);
                Object currentStepInVal;
                if (currentStepIn != null) {
                    if (((WorkflowStepInput) currentStepIn).getDefault() != null) {
                        currentStepInVal = ((WorkflowStepInput) currentStepIn).getDefault();
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

                associatedInput = existingCWLInputs.stream().filter(in -> {
                    if (((WorkflowInputParameter) in).getId().get().contains("#")) {
                        return ((WorkflowInputParameter) in).getId().get().endsWith("/" + currentStepInVal);
                    }
                    else {
                        return  ((WorkflowInputParameter) in).getId().get().equals(currentStepInVal);
                    }
                }).findFirst().orElse(null);
            }
            Object type = associatedInput != null ? ((WorkflowInputParameter)associatedInput).getType() : Any.ANY.toString();
            input.setType(type);
            CommandLineBinding inputBinding = new CommandLineBindingImpl();
            // check if source name is found in bash arguments
            String foundPrefix = bashArguments.stream().filter(arg -> arg.matches("-?" + source.getName() + "(=.*)?")).findFirst().orElse(null);
            if (foundPrefix != null) {
                int chIndex = foundPrefix.indexOf("=");
                // if source name is found containing equal sign, add only the first part; else add the found prefix
                if (chIndex > -1) {
                    inputBinding.setPrefix(Optional.of(foundPrefix.substring(0,chIndex + 1 )));
                } else {
                    inputBinding.setPrefix(Optional.of(foundPrefix));
                }
            } else {
                inputBinding.setPrefix(Optional.of("-" + source.getName()));
            }
            inputBinding.setValueFrom("$(self.path)");
            input.setInputBinding(Optional.of(inputBinding));
            inputs.add(input);
        }

        // add the parameters to job CWL file, inputs section and YAML file
        if (component instanceof ProcessingComponent && ((ProcessingComponent)component).getParameterDescriptors().size() > 0) {
            Map<Object, Object> yamlFileList = new HashMap<>();

            // get in element from current step
            List<Object> inList = ((WorkflowStep)currentStep).getIn();
            // main Worflow file has only 1 step. The one that calls the second Workflow file
            List<Object> mainWfInList = ((WorkflowStep)mainExistingWorkflow.getSteps().get(0)).getIn();

            for (ParameterDescriptor desc : ((ProcessingComponent)component).getParameterDescriptors()) {
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
                            inputBinding.setPrefix(Optional.of(foundPrefix.substring(0,chIndex + 1 )));
                        } else {
                            inputBinding.setPrefix(Optional.of(foundPrefix));
                        }
                    } else {
                        inputBinding.setPrefix(Optional.of("-" + desc.getLabel()));
                    }
                    input.setInputBinding(Optional.of(inputBinding));
                    inputs.add(input);

                    // create parameter input for workflow and add it to inputs
                    WorkflowInputParameter workflowInputParameter = new WorkflowInputParameterImpl();
                    workflowInputParameter.setId(Optional.of(desc.getName()));
                    workflowInputParameter.setType(JavaType.fromClass(desc.getDataType()).friendlyName());
                    workflowInputParameter.setLabel(Optional.of(desc.getDescription()));
                    existingCWLInputs.add(workflowInputParameter);

                    if (mainWfExistingCWLInputs.stream().filter( i ->
                            ((WorkflowInputParameter)i).getId().get().endsWith("/" + MAIN_WF_INPUT_ID_PREFIX + desc.getName())).findAny().isEmpty()) {
                        WorkflowInputParameter mainWorkflowInputParameter = new WorkflowInputParameterImpl();
                        mainWorkflowInputParameter.setId(Optional.of(MAIN_WF_INPUT_ID_PREFIX + desc.getName()));
                        mainWorkflowInputParameter.setType(JavaType.fromClass(desc.getDataType()).friendlyName());
                        mainWorkflowInputParameter.setLabel(Optional.of(desc.getDescription()));
                        mainWfExistingCWLInputs.add(mainWorkflowInputParameter);
                    }

                    // create input for step workflow add it to in list
                    if (inList.stream().filter( i ->
                            ((WorkflowStepInput)i).getId().get().endsWith("/" + desc.getLabel())).findAny().isEmpty()) {
                        WorkflowStepInput stepInput = new WorkflowStepInputImpl();
                        stepInput.setId(Optional.of(desc.getLabel()));
                        stepInput.setDefault(desc.getName());
                        inList.add(stepInput);

                        WorkflowStepInput mainStepInput = new WorkflowStepInputImpl();
                        mainStepInput.setId(Optional.of(desc.getName()));
                        mainStepInput.setDefault(MAIN_WF_INPUT_ID_PREFIX + desc.getName());
                        mainWfInList.add(mainStepInput);
                    }

                    for (Variable t : task.getInputParameterValues()) {
                        if (t.getKey().equals(desc.getName())) {
                            // create parameter input for YAML file
                            yamlFileList.put(MAIN_WF_INPUT_ID_PREFIX + desc.getName(), JavaType.fromClass(desc.getDataType()).getConverter().apply(t.getValue()));
                            break;
                        }
                    }
                }
            }
            // add parameter input for YAML file
            createOrUpdateYamlFile(path, yamlFileList);
            // add inputs to job CWL files
            final String mainExistingWfId = mainExistingWorkflow.getId().get().contains("#") ?
                    mainExistingWorkflow.getId().get().substring(mainExistingWorkflow.getId().get().lastIndexOf("#") + 1) : mainExistingWorkflow.getId().get();
            final String existingWorkflowId = existingWorkflow.getId().get().contains("#") ?
                    existingWorkflow.getId().get().substring(existingWorkflow.getId().get().lastIndexOf("#") + 1) : existingWorkflow.getId().get();

            Object mainExistingWfToRemove = cwlGraphList.stream().filter(obj ->
                    ((LinkedHashMap<?, ?>) obj).get("id").equals(mainExistingWfId)).findFirst().orElse(null);
            Object existingWfToRemove = cwlGraphList.stream().filter(obj ->
                    ((LinkedHashMap<?, ?>) obj).get("id").equals(existingWorkflowId)).findFirst().orElse(null);

            int mainExistingWfIdx = mainExistingWfToRemove != null ? cwlGraphList.indexOf(mainExistingWfToRemove) : 0;
            int existingWfIdx = existingWfToRemove != null ? cwlGraphList.indexOf(existingWfToRemove) : 1;
            cwlGraphList.remove(mainExistingWfToRemove);
            cwlGraphList.remove(existingWfToRemove);

            cwlGraphList.add(mainExistingWfIdx, mainExistingWorkflow.save());
            cwlGraphList.add(existingWfIdx, existingWorkflow.save());
        }

        commandLineTool.setInputs(inputs);

        // arguments associated with the outputs
        List<Object> argumentsList = new LinkedList<>();
        int argumentPosition = baseCommandArray.size();
        // create task outputs and arguments based on Component targets
        for (TargetDescriptor target : component.getTargets()) {
            argumentPosition++;
            CommandOutputParameter output = new CommandOutputParameterImpl();
            CommandLineBinding argumentOut = new CommandLineBindingImpl();

            String outputId = target.getName() + "_" + task.getWorkflowNodeId();

            output.setId(Optional.of(outputId));
            argumentOut.setValueFrom("outputs." + outputId);

            output.setType(getInOutType(target.getDataDescriptor().getFormatType(), target.getDataDescriptor().getLocation()));
            CommandOutputBinding outputBinding = new CommandOutputBindingImpl();
            String targetLocation = target.getDataDescriptor().getLocation();
            String targetExtension = targetLocation.substring(targetLocation.lastIndexOf("."));
            //outputBinding.setGlob(target.getDataDescriptor().getLocation());
            outputBinding.setGlob("*" + targetExtension);
            output.setOutputBinding(Optional.of(outputBinding));

            outputs.add(output);

            argumentOut.setPosition(argumentPosition);
            // set prefix
            // check if target name is found in bash arguments
            String foundPrefix = bashArguments.stream().filter(arg -> arg.matches("-" + target.getName() + "(=.*)?")).findFirst().orElse(null);
            if (foundPrefix != null) {
                int chIndex = foundPrefix.indexOf("=");
                // if source name is found containing equal sign, add only the first part; else add the found prefix
                if (chIndex > -1) {
                    argumentOut.setPrefix(Optional.of(foundPrefix.substring(0,chIndex + 1 )));
                } else {
                    argumentOut.setPrefix(Optional.of(foundPrefix));
                }
            } else {
                argumentOut.setPrefix(Optional.of("-" + target.getName()));
            }
            argumentsList.add(argumentOut);
        }

        for (int i = bashArgsLastNonParamPosition; i < bashArguments.size(); i++) {
            CommandLineBinding argumentOut = new CommandLineBindingImpl();
            if (bashArguments.get(i).startsWith("-")) {
                String argOutPrefix;
                String argOutValue;
                if (bashArguments.get(i).matches("-\\w+=(\\w|\\/|\\.)+")) {
                    String[] commandList = bashArguments.get(i).split("=");
                    argOutPrefix = commandList[0] + "=";
                    argumentOut.setPosition(argumentPosition);
                    argOutValue = commandList[1];
                } else {
                    argOutPrefix = bashArguments.get(i);
                    argOutValue = bashArguments.get(i + 1);
                    i++;
                }
                boolean alreadyAdded = argumentsList.stream().anyMatch(a -> ((CommandLineBinding)a).getPrefix().get().equals(argOutPrefix)) ||
                                        inputs.stream().anyMatch(in -> ((CommandInputParameter)in).getInputBinding().get().getPrefix().get().equals(argOutPrefix));
                if (!alreadyAdded) {
                    argumentPosition++;
                    argumentOut.setPosition(argumentPosition);
                    argumentOut.setPrefix(Optional.of(argOutPrefix));
                    argumentOut.setValueFrom(argOutValue);
                    argumentsList.add(argumentOut);
                }
            }
        }
        commandLineTool.setArguments(Optional.of(argumentsList));
        commandLineTool.setOutputs(outputs);
        Object commandLineToolToRemove = cwlGraphList.stream().filter(obj ->
                ((LinkedHashMap<?, ?>) obj).get("id").equals(commandLineTool.getId().get())).findFirst().orElse(null);
        int commandLineToolIdx = commandLineToolToRemove != null ? cwlGraphList.indexOf(commandLineToolToRemove) : cwlGraphList.size();
        cwlGraphList.remove(commandLineToolToRemove);
        cwlGraphList.add(commandLineToolIdx, commandLineTool.save());
    }

    /**
     * Transforms the object and write it to file
     * @param path
     * @param object
     */
    private void writeToFile(Path path, Map<Object, Object> object) {
        Map<Object, Object> tempList = new LinkedHashMap<>();
        MyYAMLFactory factory =  new MyYAMLFactory();
        factory.enable(MyYAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(MyYAMLGenerator.Feature.WRITE_DOC_START_MARKER);

        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        try {
            if (Files.exists(path)) {
                Map<Object, Object> existingInputList = mapper.readValue(path.toFile(), Map.class);
                if (existingInputList != null && existingInputList.size() > 0) {
                    tempList.putAll(existingInputList);
                }
            }
            if (object != null && object.size() > 0) {
                tempList.putAll(object);
                mapper.writeValue(path.toFile(), tempList);
            }
        } catch (IOException e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
    }

    /**
     * Order workflow nodes based on orderNodes method and node level
     * @param workflowNodeDesc
     * @return list of workflow node descriptor
     */
    private List<WorkflowNodeDescriptor> orderWfDescNodes(WorkflowDescriptor workflowNodeDesc) {
        List<WorkflowNodeDescriptor> nodes = workflowNodeDesc.orderNodes(workflowNodeDesc.getNodes(),null);
        // order nodes by level too.
        return nodes.stream().sorted(Comparator.comparing(WorkflowNodeDescriptor::getLevel)).collect(Collectors.toList());
    }
}
