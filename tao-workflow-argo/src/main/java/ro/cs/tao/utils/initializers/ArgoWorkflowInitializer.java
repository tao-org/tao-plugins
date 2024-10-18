package ro.cs.tao.utils.initializers;

import io.fabric8.kubernetes.api.model.HostPathVolumeSource;
import io.fabric8.kubernetes.api.model.Volume;
import ro.cs.tao.argo.workflow.model.*;
import ro.cs.tao.utils.executors.ExecutionUnit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ro.cs.tao.utils.constants.ArgoConstants.*;

public class ArgoWorkflowInitializer {

    private final ArgoWorkflow argoWorkflow;
    private final Path workflowPath;

    private final ExecutionUnit executionUnit;

    public ArgoWorkflowInitializer(ArgoWorkflow argoWorkflow, Path workflowPath, ExecutionUnit executionUnit) {
        this.argoWorkflow = argoWorkflow;
        this.workflowPath = workflowPath;
        this.executionUnit = executionUnit;
    }


    public void initializeArgoWorkflow() {
        argoWorkflow.setApiVersion(ARGO_VERSION);
        argoWorkflow.setKind(ARGO_KIND);
        initializeWorkflowMetadata();
        initializeWorkflowSpec();
    }

    private void initializeWorkflowMetadata() {
        WorkflowMetadata metadata = new WorkflowMetadata();
        String workflowName = workflowPath.getFileName().toString().substring(0,workflowPath.getFileName().toString().indexOf("."));
        String modifiedWorkflowName = workflowName.toLowerCase().replaceAll("_", "");
        metadata.setGenerateName(modifiedWorkflowName);
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
        List<Volume> volumes = getVolumes();
        spec.setVolumes(volumes);
    }

    private List<Volume> getVolumes() {
        List<Volume> volumes = new ArrayList<>();
        for(Map.Entry<String, String> entry : executionUnit.getVolumeMap().entrySet()){
            Volume volume = new Volume();
            volume.setName(getVolumeName(entry.getKey()));
            HostPathVolumeSource hostPath = new HostPathVolumeSource();
            hostPath.setPath(entry.getValue());
            volume.setHostPath(hostPath);
            volumes.add(volume);
        }
        return volumes;
    }

    private String getVolumeName(String path) {
        String[] partsOfPath = path.split("/");
        for(int i = partsOfPath.length -1 ; i > -1; i--){
            if(partsOfPath[i].matches(".*[a-zA-Z]+"))
                return partsOfPath[i];
        }
        return path;
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
}
