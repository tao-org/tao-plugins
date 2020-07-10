
package ro.cs.tao.datasource.remote.creodias.model.s1;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import ro.cs.tao.datasource.remote.creodias.model.common.Properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "type",
    "properties",
    "features"
})
public class ResultSet2 {

    @JsonProperty("type")
    private String type;
    @JsonProperty("properties")
    private Properties properties;
    @JsonProperty("features")
    private List<Feature2> features = new ArrayList<>();
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<>();

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    public ResultSet2 withType(String type) {
        this.type = type;
        return this;
    }

    @JsonProperty("properties")
    public Properties getProperties() {
        return properties;
    }

    @JsonProperty("properties")
    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public ResultSet2 withProperties(Properties properties) {
        this.properties = properties;
        return this;
    }

    @JsonProperty("features")
    public List<Feature2> getFeatures() {
        return features;
    }

    @JsonProperty("features")
    public void setFeatures(List<Feature2> features) {
        this.features = features;
    }

    public ResultSet2 withFeatures(List<Feature2> features) {
        this.features = features;
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

    public ResultSet2 withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(type).append(properties).append(features).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ResultSet2) == false) {
            return false;
        }
        ResultSet2 rhs = ((ResultSet2) other);
        return new EqualsBuilder().append(type, rhs.type).append(properties, rhs.properties).append(features, rhs.features).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
