package ro.cs.tao.execution.ogc.model;

import java.util.HashMap;
import java.util.Map;

public class ExecuteRequest {
    private Map<String, Object> inputs = new HashMap<>();
    private Map<String, Object> outputs = new HashMap<>();
    private String response = "document";
    private String mode = "sync";

    // Getters and setters
    public Map<String, Object> getInputs() { return inputs; }
    public void setInputs(Map<String, Object> inputs) { this.inputs = inputs; }
    public Map<String, Object> getOutputs() { return outputs; }
    public void setOutputs(Map<String, Object> outputs) { this.outputs = outputs; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}