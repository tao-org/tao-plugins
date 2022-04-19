package ro.cs.tao.stac.model;

import java.util.List;
import java.util.Map;

public class Spatial {

    private List<List<Float>> bbox;
    private Map<String, Object> additionalProperties;

    public List<List<Float>> getBbox() {
        return bbox;
    }

    public void setBbox(List<List<Float>> bbox) {
        this.bbox = bbox;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}