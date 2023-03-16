package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.commons.lang3.StringUtils;
import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.Process;
import org.w3id.cwl.cwl1_2.utils.RootLoader;
import org.yaml.snakeyaml.Yaml;
import ro.cs.tao.component.*;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.util.TaskUtilities;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.workflow.NodeListOrderer;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;
import ro.cs.tao.workflow.WorkflowNodeGroupDescriptor;

import java.io.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CWLExecutor  extends Executor<Object>
        implements ExecutionDescriptorConverter {
    private ExecutionUnit unit;

    public CWLExecutor() {
    }

    public CWLExecutor(ExecutionUnit unit) {
        super(unit.getHost(), unit.getArguments(), unit.asSuperUser());
        this.unit = unit;
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

            ExecutionTask task = TaskUtilities.getTaskById(taskId);
            TaoComponent taskComponent = TaskUtilities.getComponentFor(task);

            try {
                final Path path = Paths.get(unit.getScriptTargetPath());
                if (!Files.exists(path)) {
                    long workflowId = TaskUtilities.getWorkflowId(taskId);
                    WorkflowDescriptor workflowDesc = TaskUtilities.getWorkflowDescriptor(workflowId);

                    Workflow workflow = new WorkflowImpl();
                    workflow.setClass_(Workflow_class.WORKFLOW);
                    workflow.setCwlVersion(Optional.of(CWLVersion.V1_2));

                    List inputList = new ArrayList();
                    List outputList = new ArrayList();

                    List<WorkflowNodeDescriptor> roots = workflowDesc.findRoots(workflowDesc.getNodes());
                    List<WorkflowNodeDescriptor> leaves = workflowDesc.findLeaves(workflowDesc.getNodes());

                    for (WorkflowNodeDescriptor wf  : roots) {
                        TaoComponent wfComponent = TaskUtilities.getComponentByNode(wf);
                        for(SourceDescriptor source : wfComponent.getSources()) {
                            if (!source.getName().equalsIgnoreCase("query")) {
                                WorkflowInputParameter input = new WorkflowInputParameterImpl();
                                input.setType(getInOutType(source.getDataDescriptor().getFormatType()));
                                input.setId(Optional.ofNullable(wfComponent.getTargets().get(0).getName() + "_" + wf.getId()));
                                inputList.add(input);
                            }
                        }
                    }
                    workflow.setInputs(inputList);

                    for (WorkflowNodeDescriptor wf : leaves) {
                        TaoComponent leavesTaskComponent = TaskUtilities.getComponentByNode(wf);
                        for(TargetDescriptor target : leavesTaskComponent.getTargets()) {
                            WorkflowOutputParameter output = new WorkflowOutputParameterImpl();
                            output.setId(Optional.ofNullable(target.getParentId().toLowerCase() + "_" + target.getName()));
                            output.setType(getInOutType(target.getDataDescriptor().getFormatType()));
                            String dataDescLocation = target.getDataDescriptor().getLocation();
                            String targetLocation = dataDescLocation;
                            if (dataDescLocation.contains(".")) {
                                targetLocation = dataDescLocation.substring(0, dataDescLocation.indexOf("."));
                            }
                            output.setOutputSource(target.getParentId().toLowerCase() + "/" + targetLocation);
                            outputList.add(output);
                        }
                    }
                    workflow.setOutputs(outputList);
                    List<Object> steps = createCWLSteps(unit, workflowDesc);
                    workflow.setSteps(steps);
                    writeWorkflowToFile(path, workflow.save());
                } else {
                    //TODO dstefanescu
                    Workflow doc = (WorkflowImpl) RootLoader.loadDocument(path);
                    System.out.println("*****");
                }
            } catch (Exception e) {
                logger.severe(ExceptionUtils.getStackTrace(logger, e));
            }
        }
        return 0;
    }

    private String getInOutType (DataFormat formatType) {
        if (formatType.name().equalsIgnoreCase("folder")) {
            return formatType.friendlyName();
        } else if (formatType.name().equalsIgnoreCase("other")){
            return "Other";
        } else {
            return "File";
        }
    }

    private List<Object> createCWLSteps(ExecutionUnit unit,  WorkflowDescriptor workflowNodeDesc) {
        List<Object> steps = new ArrayList<>();
       // WorkflowStep step = new WorkflowStepImpl();
        String stepFile = unit.getScriptTargetPath().substring(0,unit.getScriptTargetPath().lastIndexOf( File.separator)) /*+ File.separator + unit.getMetadata().get("name").toString().toLowerCase()*/;
        //Path path = Paths.get(stepFile);
       // step.setId(Optional.ofNullable(unit.getMetadata().get("name").toString().toLowerCase() + "_" + unit.getMetadata().get("id")));
        //step.setRun(path + ".cwl");
        Map<Long, Map<String, Object>> stepInfoMap = createStepsMap(workflowNodeDesc);
        List<WorkflowNodeDescriptor> orderedWfNodeDescList = workflowNodeDesc.orderNodes(workflowNodeDesc.getNodes(),null);

        for(WorkflowNodeDescriptor wf : orderedWfNodeDescList) {
            if (wf.getIncomingLinks().size() > 0) {
                WorkflowStep step = new WorkflowStepImpl();
                String stepId = wf.getComponentId().toLowerCase() + "_" + wf.getId();
                List<Object> inList = new ArrayList<>();
                List<Object> outList = new ArrayList<>();
                for (ComponentLink link : wf.getIncomingLinks()) {
                    WorkflowStepInput in = new WorkflowStepInputImpl();
                    in.setId(Optional.ofNullable(link.getOutput().getName() + "_" + wf.getId()));
                    stepInfoMap.get(link.getSourceNodeId()).get("out");
                    if (((HashMap)stepInfoMap.get(link.getSourceNodeId()).get("in")).size() > 0) {
                        Map linkedInput = ((HashMap) stepInfoMap.get(wf.getId()).get("in"));
                        in.setValueFrom(linkedInput.get(link.getSourceNodeId()) + "_" + link.getSourceNodeId() +
                                "/" + link.getInput().getName() + "_" + link.getSourceNodeId());
                    } else {
                        in.setValueFrom(link.getInput().getName() + "_" + link.getSourceNodeId());
                    }
                    inList.add(in);
                }
                for(Object output : (ArrayList) stepInfoMap.get(wf.getId()).get("out")) {
                    WorkflowStepOutput out = new WorkflowStepOutputImpl();
                    out.setId(Optional.ofNullable(output.toString() + "_" + wf.getId()));
                    outList.add(out);
                }
                step.setId(Optional.ofNullable(stepId));
                step.setRun(wf.getComponentId().toLowerCase() + ".cwl");
                step.setIn(inList);
                step.setOut(outList);
                steps.add(step);
            }
        }
        return steps;
    }

    private Map<Long, Map<String, Object>> createStepsMap (WorkflowDescriptor workflowNodeDesc) {
        Map<Long, Map<String, Object>> stepInfoMap = new HashMap<>();
        for(WorkflowNodeDescriptor wf : workflowNodeDesc.getNodes()) {
            Long id = wf.getId();
            Map<String, Object> obj = new HashMap<>();
            TaoComponent wfTaskComponent = TaskUtilities.getComponentByNode(wf);
            Map<Long, String> in = new HashMap<>();
            for(ComponentLink link : wf.getIncomingLinks()) {
                in.put(link.getSourceNodeId(),link.getOutput().getName());
            }
            obj.put("in", in);
            List<String> out = new ArrayList<>();
            for(TargetDescriptor target : wfTaskComponent.getTargets()){
                out.add(target.getName());
            }
            obj.put("out", out);
            obj.put("name", wf.getComponentId().toLowerCase() + "_" + wf.getId());
            stepInfoMap.put(id, obj);
        }
        return stepInfoMap;
    }

    private void writeWorkflowToFile(Path path, Map<Object, Object> workflow) {
        String workflowString = "";
        final StringBuilder builder = new StringBuilder();
        for (Map.Entry obj : workflow.entrySet()) {
            if (obj.getKey().equals("inputs") || obj.getKey().equals("outputs")) {
                System.out.println();
                workflowString += obj.getKey() + ":" + System.lineSeparator();
                for (Object elem: ((Map)obj.getValue()).entrySet()) {
                    workflowString += " " + ((Map.Entry) elem).getKey() + ":" + System.lineSeparator();
                    for (Object subElem : ((Map)((Map.Entry) elem).getValue()).entrySet()) {
                        workflowString += "  " + ((Map.Entry) subElem).getKey() + ":" + ((Map.Entry) subElem).getValue() + System.lineSeparator();
                    }
                }
            } else if (obj.getKey().equals("steps")) {
                System.out.println();
                workflowString += obj.getKey() + ":" + System.lineSeparator();
                for (Object elem: ((Map)obj.getValue()).entrySet()) {
                    workflowString += " " + ((Map.Entry) elem).getKey() + ":" + System.lineSeparator();
                    for (Object stepElem: ((Map)((Map.Entry) elem).getValue()).entrySet()) {
                        if (((Map.Entry) stepElem).getValue() instanceof Map) {
                            workflowString += "  " + ((Map.Entry) stepElem).getKey() + ":" + System.lineSeparator();
                            for (Object subElem : ((Map) ((Map.Entry) stepElem).getValue()).entrySet()) {
                                workflowString += "   " + ((Map.Entry) subElem).getKey() + ": " + ((Map.Entry) subElem).getValue() + System.lineSeparator();
                            }
                        } else if (((Map.Entry) stepElem).getValue() instanceof ArrayList) {
                            workflowString += "  " + ((Map.Entry) stepElem).getKey() + ": " + ((Map.Entry) stepElem).getValue().toString().replaceAll("\"","") + System.lineSeparator();
                        } else {
                            workflowString += "  " + ((Map.Entry) stepElem).getKey() + ": " + ((Map.Entry) stepElem).getValue() + System.lineSeparator();
                        }
                    }
                }
            } else if (obj.getKey().equals("class")) {
                workflowString += obj.getKey() + ": \"" + obj.getValue() + "\"" + System.lineSeparator();
            } else if (obj.getKey().equals("cwlVersion")) {
                workflowString += obj.getKey() + ": \"" + obj.getValue().toString().toLowerCase().replace("_", ".") + "\"" + System.lineSeparator();
            } else {
                workflowString += obj.getKey() + ": " + obj.getValue() + System.lineSeparator();
            }
        }
        builder.append(workflowString);
        try {
            Files.write(path, builder.toString().getBytes(), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println();
    }
}
