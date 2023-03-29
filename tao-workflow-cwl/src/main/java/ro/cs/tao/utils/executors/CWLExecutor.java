package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.utils.RootLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;
import ro.cs.tao.component.*;
import ro.cs.tao.datasource.DataSourceComponent;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.persistence.ExecutionTaskProvider;
import ro.cs.tao.execution.util.TaskUtilities;
import ro.cs.tao.persistence.WorkflowProvider;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;

import java.io.File;
import java.io.FileWriter;
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

    public CWLExecutor() {
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
    }

    public CWLExecutor(ExecutionUnit unit) {
        super(unit.getHost(), unit.getArguments(), unit.asSuperUser());
        this.unit = unit;
        this.taskProvider = SpringContextBridge.services().getService(ExecutionTaskProvider.class);
        this.workflowProvider = SpringContextBridge.services().getService(WorkflowProvider.class);
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
                            if (!DataSourceComponent.QUERY_PARAMETER.equals(source.getName())) {
                                WorkflowInputParameter input = new WorkflowInputParameterImpl();
                                input.setType(getInOutType(source.getDataDescriptor().getFormatType()));
                                input.setId(Optional.of(wfComponent.getTargets().get(0).getName() + "_" + wf.getId()));
                                inputList.add(input);
                            } else {
                                yamlFileObj.put("class", File_class.FILE.getDocVal());
                                yamlFileObj.put("path", FileUtilities.toUnixPath(source.getDataDescriptor().getLocation()));
                                yamlFileList.put(wfComponent.getTargets().get(0).getName() + "_" + wf.getId(), yamlFileObj);
                            }
                        }
                    }
                    cwlWorkflow.setInputs(inputList);
                    createYamlFile(path, yamlFileList);

                    for (WorkflowNodeDescriptor wf : leaves) {
                        TaoComponent leavesTaskComponent = TaskUtilities.getComponentFor(wf);
                        for(TargetDescriptor target : leavesTaskComponent.getTargets()) {
                            WorkflowOutputParameter output = new WorkflowOutputParameterImpl();
                            output.setId(Optional.of(target.getParentId().toLowerCase() + "_" + target.getName()));
                            output.setType(getInOutType(target.getDataDescriptor().getFormatType()));
                            output.setOutputSource(target.getParentId().toLowerCase() + "_" + wf.getId() + "/" + target.getName() + "_" + wf.getId());
                            outputList.add(output);
                        }
                    }
                    cwlWorkflow.setOutputs(outputList);
                    List<Object> steps = createWorkflowCWLSteps(workflowDesc);
                    cwlWorkflow.setSteps(steps);
                    createStepCWL(path, workflowDesc, task);
                    writeToFile(path, cwlWorkflow.save());
                } else {
                    createStepCWL(path, workflowDesc, task);
                }
            } catch (Exception e) {
                logger.severe(ExceptionUtils.getStackTrace(logger, e));
            }
        }
        return 0;
    }

    private String getInOutType (DataFormat formatType) {
        final String retVal;
        switch (formatType) {
            case FOLDER:
                retVal = formatType.friendlyName();
                break;
            case OTHER:
                //retVal = StringUtilities.toFirstCaps(formatType.name());
                retVal = "File";
                break;
            default:
                retVal = "File";
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
                step.setRun(wf.getName().toLowerCase() + ".cwl");
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

    private void createYamlFile(Path path, Map<Object, Object> inputList) {
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
            mapper.writeValue(yamlFile.toFile(), inputList);
        } catch (IOException e) {
            logger.severe(ExceptionUtils.getStackTrace(logger, e));
        }
    }

    private void createStepCWL(Path path, WorkflowDescriptor workflowDesc, ExecutionTask task) {
        Path stepPath = Paths.get(path.getParent().toString() + File.separator + this.unit.getMetadata().get("name").toString().toLowerCase() + ".cwl");
        if (!Files.exists(stepPath)) {
            TaoComponent component = TaskUtilities.getComponentFor(task);
            CommandLineTool commandLineTool = new CommandLineToolImpl();

            commandLineTool.setClass_(CommandLineTool_class.COMMANDLINETOOL);
            commandLineTool.setCwlVersion(Optional.of(CWLVersion.V1_2));

            List<Object> baseCommandArray = new ArrayList();
            //the last argument is not added, represents input, outputs and parameters
            for (int i=0; i< this.unit.getArguments().size(); i++) {
                if (i == this.unit.getArguments().size() - 1) {
                    int indexEnd = this.unit.getArguments().get(i).indexOf(" ");
                    baseCommandArray.add(this.unit.getArguments().get(i).substring(0, indexEnd).replaceAll("\"",""));
                }/* else {
                    baseCommandArray.add(this.unit.getArguments().get(i));
                }*/
            }


            List<Object> requirementsList = new ArrayList<>();
            if (this.unit.getMinDisk() != null || this.unit.getMinMemory() != null) {
                ResourceRequirement requirement = new ResourceRequirementImpl();
                requirement.setClass_(ResourceRequirement_class.RESOURCEREQUIREMENT);
                requirement.setRamMin(this.unit.getMinMemory() != null ? this.unit.getMinMemory() : this.unit.getMinDisk());
                requirementsList.add(requirement);
            }
            commandLineTool.setRequirements(Optional.of(requirementsList));

            if (component instanceof ProcessingComponent && ((ProcessingComponent)component).getParameterDescriptors().size() > 0) {
                for (ParameterDescriptor desc : ((ProcessingComponent)component).getParameterDescriptors()) {
                    for (Variable t : task.getInputParameterValues()) {
                        if (t.getKey().equals(desc.getName())) {
                            baseCommandArray.add(desc.getName() + " " + t.getValue());
                            break;
                        }
                    }
                }
            }

            commandLineTool.setBaseCommand(baseCommandArray);

            List<Object> inputs = new ArrayList<>();
            List<Object> outputs = new ArrayList<>();
            for (SourceDescriptor source : component.getSources()) {
                CommandInputParameter input = new CommandInputParameterImpl();
                input.setId(Optional.of(source.getName() + "_" + task.getWorkflowNodeId()));
                input.setType(getInOutType(source.getDataDescriptor().getFormatType()));
                inputs.add(input);
            }
            commandLineTool.setInputs(inputs);

            for (TargetDescriptor target : component.getTargets()) {
                CommandOutputParameter output = new CommandOutputParameterImpl();
                output.setId(Optional.of(target.getName() + "_" + task.getWorkflowNodeId()));
                output.setType(getInOutType(target.getDataDescriptor().getFormatType()));
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
            Files.write(path, builder.toString().getBytes(), StandardOpenOption.CREATE);
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
        } else {
            objectString.append(ident).append(object.getKey()).append(": ").append(object.getValue());
        }
    }
}
