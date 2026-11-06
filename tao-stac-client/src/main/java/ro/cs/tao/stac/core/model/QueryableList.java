package ro.cs.tao.stac.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryableList {
    @JsonProperty("$id")
    private String id;
    private String type;
    private String title;
    @JsonProperty("$schema")
    private String schema;
    private Map<String, Queryable> properties;
    private boolean additionalProperties;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Map<String, Queryable> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Queryable> properties) {
        this.properties = properties;
    }
}
