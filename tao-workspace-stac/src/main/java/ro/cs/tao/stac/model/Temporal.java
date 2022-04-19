package ro.cs.tao.stac.model;

import java.util.List;
import java.util.Map;

public class Temporal {

    private List<List<String>> interval;
    private Map<String, Object> additionalProperties;

    public List<List<String>> getInterval() {
        return interval;
    }

    public void setInterval(List<List<String>> interval) {
        this.interval = interval;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}