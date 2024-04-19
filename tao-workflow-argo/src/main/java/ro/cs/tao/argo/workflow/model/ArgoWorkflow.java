package ro.cs.tao.argo.workflow.model;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import ro.cs.tao.argo.workflow.model.WorkflowMetadata;
import ro.cs.tao.argo.workflow.model.WorkflowSpec;

import java.util.Map;

public class ArgoWorkflow implements KubernetesResource {
    private String apiVersion;
    private String kind;
    private WorkflowMetadata metadata;
    private WorkflowSpec spec;
    private Map<String, Object> status;

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public WorkflowMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(WorkflowMetadata metadata) {
        this.metadata = metadata;
    }

    public WorkflowSpec getSpec() {
        return spec;
    }

    public void setSpec(WorkflowSpec spec) {
        this.spec = spec;
    }

    public Map<String, Object> getStatus() {
        return status;
    }

    public void setStatus(Map<String, Object> status) {
        this.status = status;
    }
}
