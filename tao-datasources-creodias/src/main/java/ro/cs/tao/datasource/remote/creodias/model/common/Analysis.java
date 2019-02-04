
package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "query",
    "language",
    "analyze",
    "processingTime"
})
public class Analysis {

    @JsonProperty("query")
    private String query;
    @JsonProperty("language")
    private String language;
    @JsonProperty("analyze")
    private Analyze analyze;
    @JsonProperty("processingTime")
    private Double processingTime;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("query")
    public String getQuery() {
        return query;
    }

    @JsonProperty("query")
    public void setQuery(String query) {
        this.query = query;
    }

    public Analysis withQuery(String query) {
        this.query = query;
        return this;
    }

    @JsonProperty("language")
    public String getLanguage() {
        return language;
    }

    @JsonProperty("language")
    public void setLanguage(String language) {
        this.language = language;
    }

    public Analysis withLanguage(String language) {
        this.language = language;
        return this;
    }

    @JsonProperty("analyze")
    public Analyze getAnalyze() {
        return analyze;
    }

    @JsonProperty("analyze")
    public void setAnalyze(Analyze analyze) {
        this.analyze = analyze;
    }

    public Analysis withAnalyze(Analyze analyze) {
        this.analyze = analyze;
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

    public Analysis withProcessingTime(Double processingTime) {
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

    public Analysis withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(query).append(language).append(analyze).append(processingTime).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Analysis) == false) {
            return false;
        }
        Analysis rhs = ((Analysis) other);
        return new EqualsBuilder().append(query, rhs.query).append(language, rhs.language).append(analyze, rhs.analyze).append(processingTime, rhs.processingTime).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
