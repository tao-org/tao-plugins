package ro.cs.tao.argo.workflow.model;

import io.fabric8.kubernetes.api.model.KubernetesResource;

public class WorkflowParameter implements KubernetesResource {
    private String defaultValue;
    private String description;
    private String name;
    private String value;

    private ValueFrom valueFrom;

    public ValueFrom getValueFrom() {
        return valueFrom;
    }

    public void setValueFrom(ValueFrom valueFrom) {
        this.valueFrom = valueFrom;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
