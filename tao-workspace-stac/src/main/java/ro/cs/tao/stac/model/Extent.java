package ro.cs.tao.stac.model;

import java.util.Map;

public class Extent {

    private Temporal temporal;
    private Spatial spatial;
    private Map<String, Object> additionalProperties;

    public Temporal getTemporal() {
        return temporal;
    }

    public void setTemporal(Temporal temporal) {
        this.temporal = temporal;
    }

    public Spatial getSpatial() {
        return spatial;
    }

    public void setSpatial(Spatial spatial) {
        this.spatial = spatial;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}