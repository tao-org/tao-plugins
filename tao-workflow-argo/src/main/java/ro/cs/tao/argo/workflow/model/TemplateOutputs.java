package ro.cs.tao.argo.workflow.model;

import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.List;

public class TemplateOutputs implements KubernetesResource {
    private String exitCode;
    private String result;
    private List<WorkflowArtifact> artifacts;
    private List<WorkflowParameter> parameters;

    public String getExitCode() {
        return exitCode;
    }

    public void setExitCode(String exitCode) {
        this.exitCode = exitCode;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

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
