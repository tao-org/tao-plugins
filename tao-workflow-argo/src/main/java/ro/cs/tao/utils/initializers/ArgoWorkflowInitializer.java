package ro.cs.tao.utils.initializers;

import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.Volume;
import ro.cs.tao.argo.workflow.model.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static ro.cs.tao.utils.constants.ArgoConstants.*;
import static ro.cs.tao.utils.constants.ArgoConstants.ENTRYPOINT;

public class ArgoWorkflowInitializer {

    private final ArgoWorkflow argoWorkflow;
    private final Path workflowPath;

    public ArgoWorkflowInitializer(ArgoWorkflow argoWorkflow, Path workflowPath) {
        this.argoWorkflow = argoWorkflow;
        this.workflowPath = workflowPath;
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
}
