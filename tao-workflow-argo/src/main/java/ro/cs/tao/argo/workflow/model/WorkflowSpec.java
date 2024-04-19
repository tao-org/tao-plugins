package ro.cs.tao.argo.workflow.model;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.Volume;
import java.util.List;

public class WorkflowSpec implements KubernetesResource {
    private String entrypoint;
    private WorkflowArguments arguments;
    private List<Volume> volumes;
    private List<WorkflowTemplate> templates;

    public String getEntrypoint() {
        return entrypoint;
    }

    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    public WorkflowArguments getArguments() {
        return arguments;
    }

    public void setArguments(WorkflowArguments arguments) {
        this.arguments = arguments;
    }

    public List<Volume> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<Volume> volumes) {
        this.volumes = volumes;
    }

    public List<WorkflowTemplate> getTemplates() {
        return templates;
    }

    public void setTemplates(List<WorkflowTemplate> templates) {
        this.templates = templates;
    }
}
