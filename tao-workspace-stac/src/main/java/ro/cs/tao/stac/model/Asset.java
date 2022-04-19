package ro.cs.tao.stac.model;

import java.util.HashMap;
import java.util.Map;

public class Asset {

    private Classification classification;
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    public Classification getClassification() {
        return classification;
    }

    public void setClassification(Classification classification) {
        this.classification = classification;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}