
package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "originalFilters",
    "appliedFilters",
    "analysis",
    "processingTime"
})
public class Query {

    @JsonProperty("originalFilters")
    private OriginalFilters originalFilters;
    @JsonProperty("appliedFilters")
    private AppliedFilters appliedFilters;
    @JsonProperty("analysis")
    private Analysis analysis;
    @JsonProperty("processingTime")
    private Double processingTime;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("originalFilters")
    public OriginalFilters getOriginalFilters() {
        return originalFilters;
    }

    @JsonProperty("originalFilters")
    public void setOriginalFilters(OriginalFilters originalFilters) {
        this.originalFilters = originalFilters;
    }

    public Query withOriginalFilters(OriginalFilters originalFilters) {
        this.originalFilters = originalFilters;
        return this;
    }

    @JsonProperty("appliedFilters")
    public AppliedFilters getAppliedFilters() {
        return appliedFilters;
    }

    @JsonProperty("appliedFilters")
    public void setAppliedFilters(AppliedFilters appliedFilters) {
        this.appliedFilters = appliedFilters;
    }

    public Query withAppliedFilters(AppliedFilters appliedFilters) {
        this.appliedFilters = appliedFilters;
        return this;
    }

    @JsonProperty("analysis")
    public Analysis getAnalysis() {
        return analysis;
    }

    @JsonProperty("analysis")
    public void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }

    public Query withAnalysis(Analysis analysis) {
        this.analysis = analysis;
        return this;
    }

    @JsonProperty("processingTime")
    public Double getProcessingTime() {
        return processingTime;
    }

    @JsonProperty("processingTime")
    public void setProcessingTime(Double processingTime) {
        this.processingTime = processingTime;
    }

    public Query withProcessingTime(Double processingTime) {
        this.processingTime = processingTime;
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

    public Query withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(originalFilters).append(appliedFilters).append(analysis).append(processingTime).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Query) == false) {
            return false;
        }
        Query rhs = ((Query) other);
        return new EqualsBuilder().append(originalFilters, rhs.originalFilters).append(appliedFilters, rhs.appliedFilters).append(analysis, rhs.analysis).append(processingTime, rhs.processingTime).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
