package ro.cs.tao.stac.model;

import java.util.List;
import java.util.Map;

public class Geometry {

    private String type;
    private List<List<List<Float>>> coordinates;
    private Map<String, Object> additionalProperties;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<List<List<Float>>> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<List<List<Float>>> coordinates) {
        this.coordinates = coordinates;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}