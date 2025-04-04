package ro.cs.tao.argo.workflow.model;

import java.util.List;

public class DAGTask {
    private WorkflowArguments arguments;
    private List<String> dependencies;
    private String name;
    private String template;

    public WorkflowArguments getArguments() {
        return arguments;
    }

    public void setArguments(WorkflowArguments arguments) {
        this.arguments = arguments;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }
}
