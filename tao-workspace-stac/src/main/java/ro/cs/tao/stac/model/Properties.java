package ro.cs.tao.stac.model;

import java.util.Map;

public class Properties {

    private Map<String, Object> additionalProperties;

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}