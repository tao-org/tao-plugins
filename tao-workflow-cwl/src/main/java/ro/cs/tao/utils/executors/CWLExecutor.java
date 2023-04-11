package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.utils.RootLoader;
import ro.cs.tao.component.*;
import ro.cs.tao.datasource.DataSourceComponent;
import ro.cs.tao.datasource.param.JavaType;
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
        if (unit.getScriptTargetPath() != null) {
            long taskId = (Long) unit.getMetadata().get("id");
            ExecutionTask task = this.taskProvider.get(taskId);
            try {
                final Path path = Paths.get(unit.getScriptTargetPath());
                long workflowId = task.getJob().getWorkflowId();
                WorkflowDescriptor workflowDesc = this.workflowProvider.get(workflowId);
                if (!Files.exists(path)) {
                    final Workflow cwlWorkflow = new WorkflowImpl();
                    cwlWorkflow.setClass_(Workflow_class.WORKFLOW);
                    cwlWorkflow.setCwlVersion(Optional.of(CWLVersion.V1_2));

                    List<Object> inputList = new ArrayList<>();
                    List<Object> outputList = new ArrayList<>();
                    Map<Object, Object> yamlFileList = new HashMap<>();

                    List<WorkflowNodeDescriptor> roots = workflowDesc.findRoots(workflowDesc.getNodes());
                    List<WorkflowNodeDescriptor> leaves = workflowDesc.findLeaves(workflowDesc.getNodes());

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
                                yamlFileObj.put("path", FileUtilities.toUnixPath(source.getDataDescriptor().getLocation()));
                                yamlFileList.put(id, yamlFileObj);
                            }
                        }
                    }
                    cwlWorkflow.setInputs(inputList);
                    createOrUpdateYamlFile(path, yamlFileList);

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
                    createStepCWL(path, workflowDesc, task, cwlWorkflow);
                    writeToFile(path, cwlWorkflow.save());
                } else {
                    WorkflowNodeDescriptor node = this.workflowNodeProvider.get(task.getWorkflowNodeId());
                    List<WorkflowNodeDescriptor> wfAncestors = workflowDesc.findAncestors(workflowDesc.getNodes(), node);
                    if (wfAncestors != null && wfAncestors.size() > 0) {
                        final Workflow existingCWL = (WorkflowImpl) RootLoader.loadDocument(path);
                        createStepCWL(path, workflowDesc, task, existingCWL);
                    } else {
                        System.out.println(String.format("Files for job [%s] already exist!", path));
                    }
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

    private List<Object> createWorkflowCWLSteps(WorkflowDescriptor workflowNodeDesc) {
        List<Object> steps = new ArrayList<>();
        Map<Long, Map<String, Object>> stepInfoMap = createStepsMap(workflowNodeDesc);
        List<WorkflowNodeDescriptor> orderedWfNodeDescList = workflowNodeDesc.orderNodes(workflowNodeDesc.getNodes(),null);

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

    private void createOrUpdateYamlFile(Path path, Map<Object, Object> inputList) {
        Map<Object, Object> tempList = new LinkedHashMap<>();

        String yamlPathParent = path.getParent().toString();
        String yamlFileName = path.getFileName().toString().replace(".cwl", ".yaml");
        final Path yamlFile =  Paths.get(yamlPathParent + File.separator + yamlFileName);

        YAMLFactory factory =  new YAMLFactory();
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);

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

    private void createStepCWL(Path path, WorkflowDescriptor workflowDesc, ExecutionTask task, Workflow existingWorkflow) {
        Path stepPath = Paths.get(path.getParent().toString() + File.separator + this.unit.getMetadata().get("name").toString().toLowerCase() + "_" + task.getWorkflowNodeId() + ".cwl");
        if (!Files.exists(stepPath)) {
            TaoComponent component = TaskUtilities.getComponentFor(task);
            CommandLineTool commandLineTool = new CommandLineToolImpl();

            commandLineTool.setClass_(CommandLineTool_class.COMMANDLINETOOL);
            commandLineTool.setCwlVersion(Optional.of(CWLVersion.V1_2));

            List<Object> baseCommandArray = new ArrayList();
            // only last argument is taken into account
            for (int i=0; i< this.unit.getArguments().size(); i++) {
                // only the job argument is added
                if (i == this.unit.getArguments().size() - 1) {
                    String arguments = this.unit.getArguments().get(i);
                    int indexEnd = arguments.indexOf(" ");
                    int secondIndexStart = arguments.indexOf(" \\");
                    baseCommandArray.add(arguments.substring(0, indexEnd).replaceAll("\"",""));
                    if (secondIndexStart > 0) {
                        int secondIndexEnd = arguments.substring(secondIndexStart + 1).indexOf(" ");
                        baseCommandArray.add(arguments.substring(secondIndexStart + 1, secondIndexEnd).toString().replaceAll("\"","").replaceAll("\\\\","/"));
                    }
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
                requirement.setDockerImageId(Optional.of(this.unit.getContainerUnit().getContainerRegistry() + "/" + this.unit.getContainerUnit().getContainerName()));
                requirementsList.add(requirement);
            }
            commandLineTool.setRequirements(Optional.of(requirementsList));

            commandLineTool.setBaseCommand(baseCommandArray);

            List<Object> inputs = new ArrayList<>();
            List<Object> outputs = new ArrayList<>();

            // get current steps form Workflow
            Object currentStep = existingWorkflow.getSteps().stream().filter(s->((WorkflowStep)s).getId().get().equals(component.getId().toLowerCase() + "_" + task.getWorkflowNodeId())).findFirst().orElse(null);

            // get inputs
            List<Object> existingCWLInputs = existingWorkflow.getInputs();

            for (SourceDescriptor source : component.getSources()) {
                String inputId = source.getName() + "_" + task.getWorkflowNodeId();
                CommandInputParameter input = new CommandInputParameterImpl();
                input.setId(Optional.of(inputId));
                Object currentStepIn = ((WorkflowStep)currentStep).getIn().stream().filter(in -> ((WorkflowStepInput)in).getId().get().equals(inputId)).findFirst().orElse(null);
                Object currentStepInVal = currentStepIn != null ? ((WorkflowStepInput)currentStepIn).getDefault() : null;
                Object associatedInput = existingCWLInputs.stream().filter(in->((WorkflowInputParameter)in).getId().get().equals(currentStepInVal)).findFirst().orElse(null);
                Object type = associatedInput != null ? ((WorkflowInputParameter)associatedInput).getType() : Any.ANY.getDocVal();
                input.setType(type);
                CommandLineBinding inputBinding = new CommandLineBindingImpl();
                inputBinding.setPrefix(Optional.of("-" + source.getName()));
                inputBinding.setValueFrom("$(self.path)");
                input.setInputBinding(Optional.of(inputBinding));
                inputs.add(input);
            }

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
                        inputBinding.setPrefix(Optional.of("-" + desc.getLabel()));
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
                                yamlFileList.put(desc.getName(), t.getValue());
                                break;
                            }
                        }
                    }
                }
                // add parameter input for YAML file
                createOrUpdateYamlFile(path, yamlFileList);
                // add inputs to workflow CWL file
                if (Files.exists(path)) {
                    writeToFile(path, existingWorkflow.save());
                }
            }

            commandLineTool.setInputs(inputs);

            for (TargetDescriptor target : component.getTargets()) {
                CommandOutputParameter output = new CommandOutputParameterImpl();
                output.setId(Optional.of(target.getName() + "_" + task.getWorkflowNodeId()));
                output.setType(getInOutType(target.getDataDescriptor().getFormatType(), target.getDataDescriptor().getLocation()));
                CommandOutputBinding outputBinding = new CommandOutputBindingImpl();
                outputBinding.setGlob(target.getDataDescriptor().getLocation());
                output.setOutputBinding(Optional.of(outputBinding));
                outputs.add(output);
            }
            commandLineTool.setOutputs(outputs);
            writeToFile(stepPath, commandLineTool.save());
        } else {
            System.out.println(String.format("File %s already exist!", stepPath.toString()));
        }
    }

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
}
