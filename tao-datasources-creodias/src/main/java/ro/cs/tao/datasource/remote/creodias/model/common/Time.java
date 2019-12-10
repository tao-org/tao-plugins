
package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "time:start",
    "time:end"
})
public class Time {

    @JsonProperty("time:start")
    private String timeStart;
    @JsonProperty("time:end")
    private String timeEnd;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("time:start")
    public String getTimeStart() {
        return timeStart;
    }

    @JsonProperty("time:start")
    public void setTimeStart(String timeStart) {
        this.timeStart = timeStart;
    }

    public Time withTimeStart(String timeStart) {
        this.timeStart = timeStart;
        return this;
    }

    @JsonProperty("time:end")
    public String getTimeEnd() {
        return timeEnd;
    }

    @JsonProperty("time:end")
    public void setTimeEnd(String timeEnd) {
        this.timeEnd = timeEnd;
    }

    public Time withTimeEnd(String timeEnd) {
        this.timeEnd = timeEnd;
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

    public Time withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(timeStart).append(timeEnd).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Time) == false) {
            return false;
        }
        Time rhs = ((Time) other);
        return new EqualsBuilder().append(timeStart, rhs.timeStart).append(timeEnd, rhs.timeEnd).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
