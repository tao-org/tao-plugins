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
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class CWLExecutor extends Executor<Object>
        implements ExecutionDescriptorConverter {
    private ExecutionUnit unit;
    private final ExecutionTaskProvider taskProvider;
    private final WorkflowProvider workflowProvider;
    private final WorkflowNodeProvider workflowNodeProvider;

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

                if (createFile) {
                    System.out.println("Create file");
                    final Workflow cwlWorkflow = new WorkflowImpl();
                    cwlWorkflow.setClass_(Workflow_class.WORKFLOW);
                    cwlWorkflow.setCwlVersion(Optional.of(CWLVersion.V1_2));

                    List<Object> inputList = new ArrayList<>();
                    List<Object> outputList = new ArrayList<>();
                    Map<Object, Object> yamlFileList = new HashMap<>();

                    List<WorkflowNodeDescriptor> roots = workflowDesc.findRoots(workflowDesc.getNodes());
                    List<WorkflowNodeDescriptor> leaves = workflowDesc.findLeaves(workflowDesc.getNodes());

                    /** create the general inputs from workflow roots
                     * will be added main CWL file and to YML file
                     */
                    for (WorkflowNodeDescriptor wf  : roots) {
                        TaoComponent wfComponent = TaskUtilities.getComponentFor(wf);
                        for (SourceDescriptor source : wfComponent.getSources()) {
                            Map<Object, Object>yamlFileObj = new HashMap<>();
                            if (DataSourceComponent.QUERY_PARAMETER.equals(source.getName())) {
                                String type = getInOutType(source.getDataDescriptor().getFormatType(), source.getDataDescriptor().getLocation());
                                String id = wfComponent.getTargets().get(0).getName() + "_" + wf.getId();

                                WorkflowInputParameter input = new WorkflowInputParameterImpl();
                                input.setType(type);
                                input.setId(Optional.of(id));
                                inputList.add(input);

                                yamlFileObj.put("class", type);
                                if (source.getDataDescriptor().getLocation().contains(",")) {
                                    String[] strings = source.getDataDescriptor().getLocation().split(",");
                                    List<String> sourceList = new LinkedList<>();
                                    for (String sourceStr : strings) {
                                        sourceList.add(FileUtilities.toUnixPath(sourceStr));
                                    }
                                    yamlFileObj.put("path", sourceList.stream().collect(Collectors.joining(",")));
                                } else {
                                    yamlFileObj.put("path", FileUtilities.toUnixPath(source.getDataDescriptor().getLocation()));
                                }
                                yamlFileList.put(id, yamlFileObj);
                            }
                        }
                    }
                    cwlWorkflow.setInputs(inputList);
                    createOrUpdateYamlFile(path, yamlFileList);

                    /** create the general outputs from workflow leaves
                     * will be added main CWL file
                     */
                    for (WorkflowNodeDescriptor wf : leaves) {
                        TaoComponent leavesTaskComponent = TaskUtilities.getComponentFor(wf);
                        for(TargetDescriptor target : leavesTaskComponent.getTargets()) {
                            WorkflowOutputParameter output = new WorkflowOutputParameterImpl();
                            output.setId(Optional.of(target.getParentId().toLowerCase() + "_" + target.getName()));
                            output.setType(getInOutType(target.getDataDescriptor().getFormatType(), target.getDataDescriptor().getLocation()));
                            output.setOutputSource(target.getParentId().toLowerCase() + "_" + wf.getId() + "/" + target.getName() + "_" + wf.getId());
                            outputList.add(output);
                        }
                    }

                    cwlWorkflow.setOutputs(outputList);
                    List<Object> steps = createWorkflowCWLSteps(workflowDesc);
                    cwlWorkflow.setSteps(steps);
                    // create the CWL file for the current workflow step
                    createStepCWL(path, workflowDesc, task, cwlWorkflow);
                    // write the main CWL file
                    writeToFile(path, cwlWorkflow.save());
                } else {
                    System.out.println("Update file");
                    final Workflow existingCWL = (WorkflowImpl) RootLoader.loadDocument(path);
                    createStepCWL(path, workflowDesc, task, existingCWL);
                }
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
                retVal = Directory_class.DIRECTORY.getDocVal();
                break;
            case OTHER:
                if (filePath != null && (filePath.endsWith("/") || filePath.endsWith("\\"))) {
                    retVal = Directory_class.DIRECTORY.getDocVal();
                } else {
                    retVal = Any.ANY.getDocVal();
                }
                break;
            default:
                if (filePath != null && (filePath.endsWith("/") || filePath.endsWith("\\"))) {
                    retVal = Directory_class.DIRECTORY.getDocVal();
                } else {
                    retVal = File_class.FILE.getDocVal();
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
                step.setRun(wf.getName().toLowerCase() + "_" + wf.getId() + ".cwl");
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
        String yamlFileName = path.getFileName().toString().replace(".cwl", ".yaml");
        final Path yamlFile =  Paths.get(yamlPathParent + File.separator + yamlFileName);

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
    private void createStepCWL(Path path, WorkflowDescriptor workflowDesc, ExecutionTask task, Workflow existingWorkflow) {
        Path stepPath = Paths.get(path.getParent().toString() + File.separator + this.unit.getMetadata().get("name").toString().toLowerCase() + "_" + task.getWorkflowNodeId() + ".cwl");
        TaoComponent component = TaskUtilities.getComponentFor(task);
        CommandLineTool commandLineTool = new CommandLineToolImpl();

        commandLineTool.setClass_(CommandLineTool_class.COMMANDLINETOOL);
        commandLineTool.setCwlVersion(Optional.of(CWLVersion.V1_2));

        List<String> bashArguments;
        final boolean isBash = this.unit.getArguments().get(0).equals("/bin/bash");
        if (isBash) {
            bashArguments = Arrays.asList(this.unit.getArguments().get(2).split(" "));
        } else {
            bashArguments = this.unit.getArguments();
        }

        List<Object> baseCommandArray = new ArrayList();
        // first bash argument is always added
        baseCommandArray.add(bashArguments.get(0).replaceAll("\"", ""));
        if (bashArguments.get(1).startsWith(" \\")) {
            baseCommandArray.add(bashArguments.get(1).replaceAll("\"","").replaceAll("\\\\","/"));
        } else if (bashArguments.get(1).equals("-c")) {
            baseCommandArray.add("/bin/bash");
            int lastPosition = ListIterate.detectIndex(bashArguments, b -> b.toString().startsWith("-") && !b.toString().equals("-c"));
            for(int i = 1 ; i < lastPosition ; i++) {
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
                requirement.setDockerImageId(Optional.of(this.unit.getContainerUnit().getContainerRegistry() + "/" + this.unit.getContainerUnit().getContainerName()));
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
                        return ((WorkflowStepInput) in).getId().get().substring(chIdx + 1).equals(inputId);
                    }
                    else {
                        return  ((WorkflowStepInput) in).getId().get().equals(inputId);
                    }
                }).findFirst().orElse(null);
                Object currentStepInVal = currentStepIn != null ? ((WorkflowStepInput) currentStepIn).getDefault() : null;

                associatedInput = existingCWLInputs.stream().filter(in -> {
                    if (((WorkflowInputParameter) in).getId().get().contains("#")) {
                        int chIdx = ((WorkflowInputParameter) in).getId().get().indexOf("#");
                        return ((WorkflowInputParameter) in).getId().get().substring(chIdx + 1).equals(currentStepInVal);
                    }
                    else {
                        return  ((WorkflowInputParameter) in).getId().get().equals(currentStepInVal);
                    }
                }).findFirst().orElse(null);
            }
            Object type = associatedInput != null ? ((WorkflowInputParameter)associatedInput).getType() : Any.ANY.getDocVal();
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

                    // create input for step workflow add it to in list
                    WorkflowStepInput stepInput = new WorkflowStepInputImpl();
                    stepInput.setId(Optional.of(desc.getLabel()));
                    stepInput.setDefault(desc.getName());
                    inList.add(stepInput);

                    for (Variable t : task.getInputParameterValues()) {
                        if (t.getKey().equals(desc.getName())) {
                            // create parameter input for YAML file
                            yamlFileList.put(desc.getName(), JavaType.fromClass(desc.getDataType()).getConverter().apply(t.getValue()));
                            break;
                        }
                    }
                }
            }
            // add parameter input for YAML file
            createOrUpdateYamlFile(path, yamlFileList);
            // add inputs to job CWL file
            if (Files.exists(path)) {
                writeToFile(path, existingWorkflow.save());
            }
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
            outputBinding.setGlob(target.getDataDescriptor().getLocation());
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
        commandLineTool.setArguments(Optional.of(argumentsList));
        commandLineTool.setOutputs(outputs);
        writeToFile(stepPath, commandLineTool.save());
    }

    /**
     * Transforms the object and write it to file
     * @param path
     * @param object
     */
    private void writeToFile(Path path, Map<Object, Object> object) {
        StringBuilder objectString = new StringBuilder();
        final StringBuilder builder = new StringBuilder();
        for (Map.Entry<?, ?> obj : object.entrySet()) {
            //check if string builder is not empty and last characters represents a new line separator
            if (objectString.length() > 0 &&
                    objectString.lastIndexOf(System.lineSeparator()) + System.lineSeparator().length() != objectString.length()) {
                objectString.append(System.lineSeparator());
            }
            if (obj.getKey().equals("class")) {
                objectString.append(obj.getKey()).append(": \"").append(obj.getValue()).append("\"");
            } else if (obj.getKey().equals("cwlVersion")) {
                objectString.append(obj.getKey()).append(": \"").append(obj.getValue().toString().toLowerCase().replace("_", ".")).append("\"");
            } else {
                treatObject(objectString, obj, "");
            }
        }
        builder.append(objectString);
        try {
            Files.write(path, builder.toString().getBytes(), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
    }

    /**
     * Transform object based on type
     * @param objectString
     * @param object
     * @param ident
     */
    private void treatObject(StringBuilder objectString, Map.Entry<?, ?> object, String ident) {
        if (object.getValue() instanceof LinkedHashMap<?, ?> || object.getValue() instanceof Map<?, ?>) {
            objectString.append(ident).append(object.getKey()).append(":").append(System.lineSeparator());
            for (Map.Entry<?, ?>  elem: ((Map<?, ?>)object.getValue()).entrySet()) {
                //check if last characters represents a new line separator
                if (objectString.lastIndexOf(System.lineSeparator()) + System.lineSeparator().length() != objectString.length()) {
                    objectString.append(System.lineSeparator());
                }
                String currentIdent = ident + " ";
                if (elem.getValue() instanceof LinkedHashMap<?, ?> || elem.getValue() instanceof Map<?, ?>) {
                    treatObject(objectString, elem, currentIdent);
                } else if (elem.getValue() instanceof  ArrayList<?>) {
                    objectString.append(currentIdent).append(elem.getKey()).append(": ").append(elem.getValue().toString().replaceAll("\"", ""));
                } else {
                    objectString.append(currentIdent).append(elem.getKey()).append(": ").append(elem.getValue());
                }
            }
        } else if (object.getValue() instanceof  ArrayList<?>) {
            objectString.append(ident).append(object.getKey()).append(": ").append(object.getValue().toString().replaceAll("\"", ""));
        } else if (object.getValue() instanceof LinkedList<?>) {
            objectString.append(ident).append(object.getKey()).append(":").append(System.lineSeparator());
            for (Object elem: (LinkedList)object.getValue()) {
                String currentIdent = ident + " ";
                if (elem instanceof LinkedHashMap<?, ?> || elem instanceof Map<?, ?>) {
                   for (Map.Entry<?, ?>  el: ((Map<?, ?>) elem).entrySet()) {
                       treatObject(objectString, el, currentIdent);
                   }
                } else if (elem instanceof  ArrayList<?>) {
                    objectString.append(currentIdent).append(elem.toString().replaceAll("\"", ""));
                } else {
                    objectString.append(elem);
                }
            }
        } else {
            //check if last characters represents a new line separator
            if (objectString.lastIndexOf(System.lineSeparator()) + System.lineSeparator().length() != objectString.length()) {
                objectString.append(System.lineSeparator());
            }
            objectString.append(ident).append(object.getKey()).append(": ").append(object.getValue());
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
