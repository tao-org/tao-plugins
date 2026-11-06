package ro.cs.tao.execution.ogc.model;

import java.util.HashMap;
import java.util.Map;

public class InputDescription {
    private String title;
    private String description;
    private Map<String, Object> schema = new HashMap<>();
    private int minOccurs = 1;
    private String maxOccurs = "1";

    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getSchema() { return schema; }
    public void setSchema(Map<String, Object> schema) { this.schema = schema; }
    public int getMinOccurs() { return minOccurs; }
    public void setMinOccurs(int minOccurs) { this.minOccurs = minOccurs; }
    public String getMaxOccurs() { return maxOccurs; }
    public void setMaxOccurs(String maxOccurs) { this.maxOccurs = maxOccurs; }
}