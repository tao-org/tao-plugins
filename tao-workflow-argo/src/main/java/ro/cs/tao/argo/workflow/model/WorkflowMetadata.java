package ro.cs.tao.argo.workflow.model;

import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkflowMetadata implements KubernetesResource {
    private String generateName;
    private final Map<String, String> labels = new LinkedHashMap<>();

    public String getGenerateName() {
        return generateName;
    }

    public void setGenerateName(String generateName) {
        this.generateName = generateName;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void addLabel(String key, String value) {
        labels.put(key, value);
    }
}
