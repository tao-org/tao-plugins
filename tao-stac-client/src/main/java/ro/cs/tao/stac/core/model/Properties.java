package ro.cs.tao.stac.core.model;

import java.time.LocalDateTime;
import java.util.Map;

public class Properties {
    protected LocalDateTime datetime;
    protected Map<String, Object> additionalProperties;

    public LocalDateTime getDatetime() {
        return datetime;
    }

    public void setDatetime(LocalDateTime datetime) {
        this.datetime = datetime;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}
