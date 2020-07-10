
package ro.cs.tao.datasource.remote.creodias.model.s1;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import ro.cs.tao.datasource.remote.creodias.model.common.Geometry2;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "type",
    "id",
    "geometry",
    "properties"
})
public class Feature2 {

    @JsonProperty("type")
    private String type;
    @JsonProperty("id")
    private String id;
    @JsonProperty("geometry")
    private Geometry2 geometry;
    @JsonProperty("properties")
    private Result properties;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    public Feature2 withType(String type) {
        this.type = type;
        return this;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public Feature2 withId(String id) {
        this.id = id;
        return this;
    }

    @JsonProperty("geometry")
    public Geometry2 getGeometry() {
        return geometry;
    }

    @JsonProperty("geometry")
    public void setGeometry(Geometry2 geometry) {
        this.geometry = geometry;
    }

    public Feature2 withGeometry(Geometry2 geometry) {
        this.geometry = geometry;
        return this;
    }

    @JsonProperty("properties")
    public Result getProperties() {
        return properties;
    }

    @JsonProperty("properties")
    public void setProperties(Result properties) {
        this.properties = properties;
    }

    public Feature2 withProperties(Result properties) {
        this.properties = properties;
        return this;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    public Feature2 withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(type).append(id).append(geometry).append(properties).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Feature2) == false) {
            return false;
        }
        Feature2 rhs = ((Feature2) other);
        return new EqualsBuilder().append(type, rhs.type).append(id, rhs.id).append(geometry, rhs.geometry).append(properties, rhs.properties).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
