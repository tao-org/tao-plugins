
package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "times"
})
public class When {

    @JsonProperty("times")
    private List<Time> times = new ArrayList<Time>();
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("times")
    public List<Time> getTimes() {
        return times;
    }

    @JsonProperty("times")
    public void setTimes(List<Time> times) {
        this.times = times;
    }

    public When withTimes(List<Time> times) {
        this.times = times;
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

    public When withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(times).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof When) == false) {
            return false;
        }
        When rhs = ((When) other);
        return new EqualsBuilder().append(times, rhs.times).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
