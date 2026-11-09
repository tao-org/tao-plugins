package ro.cs.tao.execution.ogc.model;

import java.util.ArrayList;
import java.util.List;

public class ProcessDescription extends ProcessSummary {
    private List<InputDescription> inputs = new ArrayList<>();
    private List<OutputDescription> outputs = new ArrayList<>();

    public List<InputDescription> getInputs() { return inputs; }
    public void setInputs(List<InputDescription> inputs) { this.inputs = inputs; }
    public List<OutputDescription> getOutputs() { return outputs; }
    public void setOutputs(List<OutputDescription> outputs) { this.outputs = outputs; }
}