package ro.cs.tao.execution.ogc.model;

import java.util.HashMap;
import java.util.Map;

public class OutputDescription {
    private String title;
    private String description;
    private Map<String, Object> schema = new HashMap<>();

    // Getters and setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Map<String, Object> getSchema() { return schema; }
    public void setSchema(Map<String, Object> schema) { this.schema = schema; }
}