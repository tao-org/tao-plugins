package ro.cs.tao.argo.workflow.model;

import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.List;

public class WorkflowArguments implements KubernetesResource {

    private List<WorkflowArtifact> artifacts;
    private List<WorkflowParameter> parameters;

    public List<WorkflowArtifact> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<WorkflowArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    public List<WorkflowParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<WorkflowParameter> parameters) {
        this.parameters = parameters;
    }
}
