package ro.cs.tao.argo.workflow.model;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.KubernetesResource;

public class WorkflowTemplate implements KubernetesResource {
    private DAG dag;
    private String name;
    private TemplateInputs inputs;
    private TemplateOutputs outputs;
    private Container container;

    public DAG getDAG() {
        return dag;
    }

    public void setDAG(DAG dag) {
        this.dag = dag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TemplateInputs getInputs() {
        return inputs;
    }

    public void setInputs(TemplateInputs inputs) {
        this.inputs = inputs;
    }

    public TemplateOutputs getOutputs() {
        return outputs;
    }

    public void setOutputs(TemplateOutputs outputs) {
        this.outputs = outputs;
    }

    public Container getContainer() {
        return container;
    }

    public void setContainer(Container container) {
        this.container = container;
    }
}
