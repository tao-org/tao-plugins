package ro.cs.tao.utils.executors;

import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.utils.RootLoader;
import ro.cs.tao.component.ComponentLink;
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.component.TargetDescriptor;
import ro.cs.tao.datasource.DataSourceComponent;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.persistence.ExecutionTaskProvider;
import ro.cs.tao.execution.util.TaskUtilities;
import ro.cs.tao.persistence.WorkflowProvider;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.utils.StringUtilities;
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
                if (!Files.exists(path)) {
                    long workflowId = task.getJob().getWorkflowId();
                    WorkflowDescriptor workflowDesc = this.workflowProvider.get(workflowId);

                    final Workflow cwlWorkflow = new WorkflowImpl();
                    cwlWorkflow.setClass_(Workflow_class.WORKFLOW);
                    cwlWorkflow.setCwlVersion(Optional.of(CWLVersion.V1_2));

                    List<Object> inputList = new ArrayList<>();
                    List<Object> outputList = new ArrayList<>();

                    List<WorkflowNodeDescriptor> roots = workflowDesc.findRoots(workflowDesc.getNodes());
                    List<WorkflowNodeDescriptor> leaves = workflowDesc.findLeaves(workflowDesc.getNodes());

                    for (WorkflowNodeDescriptor wf  : roots) {
                        TaoComponent wfComponent = TaskUtilities.getComponentFor(wf);
                        for (SourceDescriptor source : wfComponent.getSources()) {
                            if (!DataSourceComponent.QUERY_PARAMETER.equals(source.getName())) {
                                WorkflowInputParameter input = new WorkflowInputParameterImpl();
                                input.setType(getInOutType(source.getDataDescriptor().getFormatType()));
                                input.setId(Optional.of(wfComponent.getTargets().get(0).getName() + "_" + wf.getId()));
                                inputList.add(input);
                            }
                        }
                    }
                    cwlWorkflow.setInputs(inputList);

                    for (WorkflowNodeDescriptor wf : leaves) {
                        TaoComponent leavesTaskComponent = TaskUtilities.getComponentFor(wf);
                        for(TargetDescriptor target : leavesTaskComponent.getTargets()) {
                            WorkflowOutputParameter output = new WorkflowOutputParameterImpl();
                            output.setId(Optional.of(target.getParentId().toLowerCase() + "_" + target.getName()));
                            output.setType(getInOutType(target.getDataDescriptor().getFormatType()));
                            String targetLocation = target.getDataDescriptor().getLocation();
                            if (targetLocation.contains(".")) {
                                targetLocation = targetLocation.substring(0, targetLocation.indexOf("."));
                            }
                            output.setOutputSource(target.getParentId().toLowerCase() + "/" + targetLocation);
                            outputList.add(output);
                        }
                    }
                    cwlWorkflow.setOutputs(outputList);
                    List<Object> steps = createCWLSteps(unit, workflowDesc);
                    cwlWorkflow.setSteps(steps);
                    writeWorkflowToFile(path, cwlWorkflow.save());
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
        final String retVal;
        switch (formatType) {
            case FOLDER:
                retVal = formatType.friendlyName();
                break;
            case OTHER:
                retVal = StringUtilities.toFirstCaps(formatType.name());
                break;
            default:
                retVal = "File";
        }
        return retVal;
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

        for (WorkflowNodeDescriptor wf : orderedWfNodeDescList) {
            if (wf.getIncomingLinks().size() > 0) {
                WorkflowStep step = new WorkflowStepImpl();
                String stepId = wf.getComponentId().toLowerCase() + "_" + wf.getId();
                List<Object> inList = new ArrayList<>();
                List<Object> outList = new ArrayList<>();
                for (ComponentLink link : wf.getIncomingLinks()) {
                    WorkflowStepInput in = new WorkflowStepInputImpl();
                    in.setId(Optional.of(link.getOutput().getName() + "_" + wf.getId()));
                    stepInfoMap.get(link.getSourceNodeId()).get("out");
                    if (((HashMap<?, ?>)stepInfoMap.get(link.getSourceNodeId()).get("in")).size() > 0) {
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
                    out.setId(Optional.of(output.toString() + "_" + wf.getId()));
                    outList.add(out);
                }
                step.setId(Optional.of(stepId));
                step.setRun(wf.getComponentId().toLowerCase() + ".cwl");
                step.setIn(inList);
                step.setOut(outList);
                steps.add(step);
            }
        }
        return steps;
    }

    private Map<Long, Map<String, Object>> createStepsMap (WorkflowDescriptor workflowDescriptor) {
        Map<Long, Map<String, Object>> stepInfoMap = new HashMap<>();
        for(WorkflowNodeDescriptor wf : workflowDescriptor.getNodes()) {
            Long id = wf.getId();
            Map<String, Object> obj = new HashMap<>();
            TaoComponent wfTaskComponent = TaskUtilities.getComponentFor(wf);
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
        StringBuilder workflowString = new StringBuilder();
        final StringBuilder builder = new StringBuilder();
        for (Map.Entry obj : workflow.entrySet()) {
            if (obj.getKey().equals("inputs") || obj.getKey().equals("outputs")) {
                System.out.println();
                workflowString.append(obj.getKey()).append(":").append(System.lineSeparator());
                for (Object elem: ((Map)obj.getValue()).entrySet()) {
                    workflowString.append(" ").append(((Map.Entry) elem).getKey()).append(":").append(System.lineSeparator());
                    for (Object subElem : ((Map)((Map.Entry) elem).getValue()).entrySet()) {
                        workflowString.append("  ").append(((Map.Entry) subElem).getKey()).append(":").append(((Map.Entry) subElem).getValue()).append(System.lineSeparator());
                    }
                }
            } else if (obj.getKey().equals("steps")) {
                System.out.println();
                workflowString.append(obj.getKey()).append(":").append(System.lineSeparator());
                for (Object elem: ((Map)obj.getValue()).entrySet()) {
                    workflowString.append(" ").append(((Map.Entry) elem).getKey()).append(":").append(System.lineSeparator());
                    for (Object stepElem: ((Map)((Map.Entry) elem).getValue()).entrySet()) {
                        if (((Map.Entry) stepElem).getValue() instanceof Map) {
                            workflowString.append("  ").append(((Map.Entry) stepElem).getKey()).append(":").append(System.lineSeparator());
                            for (Object subElem : ((Map) ((Map.Entry) stepElem).getValue()).entrySet()) {
                                workflowString.append("   ").append(((Map.Entry) subElem).getKey()).append(": ").append(((Map.Entry) subElem).getValue()).append(System.lineSeparator());
                            }
                        } else if (((Map.Entry) stepElem).getValue() instanceof ArrayList) {
                            workflowString.append("  ").append(((Map.Entry) stepElem).getKey()).append(": ").append(((Map.Entry) stepElem).getValue().toString().replaceAll("\"", "")).append(System.lineSeparator());
                        } else {
                            workflowString.append("  ").append(((Map.Entry) stepElem).getKey()).append(": ").append(((Map.Entry) stepElem).getValue()).append(System.lineSeparator());
                        }
                    }
                }
            } else if (obj.getKey().equals("class")) {
                workflowString.append(obj.getKey()).append(": \"").append(obj.getValue()).append("\"").append(System.lineSeparator());
            } else if (obj.getKey().equals("cwlVersion")) {
                workflowString.append(obj.getKey()).append(": \"").append(obj.getValue().toString().toLowerCase().replace("_", ".")).append("\"").append(System.lineSeparator());
            } else {
                workflowString.append(obj.getKey()).append(": ").append(obj.getValue()).append(System.lineSeparator());
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
