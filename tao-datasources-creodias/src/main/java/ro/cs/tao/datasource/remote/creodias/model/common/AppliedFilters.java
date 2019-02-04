
package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "status",
    "startDate",
    "completionDate",
    "lon",
    "lat",
    "collection"
})
public class AppliedFilters {

    @JsonProperty("status")
    private Integer status;
    @JsonProperty("startDate")
    private String startDate;
    @JsonProperty("completionDate")
    private String completionDate;
    @JsonProperty("lon")
    private Double lon;
    @JsonProperty("lat")
    private Double lat;
    @JsonProperty("collection")
    private String collection;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("status")
    public Integer getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    public AppliedFilters withStatus(Integer status) {
        this.status = status;
        return this;
    }

    @JsonProperty("startDate")
    public String getStartDate() {
        return startDate;
    }

    @JsonProperty("startDate")
    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public AppliedFilters withStartDate(String startDate) {
        this.startDate = startDate;
        return this;
    }

    @JsonProperty("completionDate")
    public String getCompletionDate() {
        return completionDate;
    }

    @JsonProperty("completionDate")
    public void setCompletionDate(String completionDate) {
        this.completionDate = completionDate;
    }

    public AppliedFilters withCompletionDate(String completionDate) {
        this.completionDate = completionDate;
        return this;
    }

    @JsonProperty("lon")
    public Double getLon() {
        return lon;
    }

    @JsonProperty("lon")
    public void setLon(Double lon) {
        this.lon = lon;
    }

    public AppliedFilters withLon(Double lon) {
        this.lon = lon;
        return this;
    }

    @JsonProperty("lat")
    public Double getLat() {
        return lat;
    }

    @JsonProperty("lat")
    public void setLat(Double lat) {
        this.lat = lat;
    }

    public AppliedFilters withLat(Double lat) {
        this.lat = lat;
        return this;
    }

    @JsonProperty("collection")
    public String getCollection() {
        return collection;
    }

    @JsonProperty("collection")
    public void setCollection(String collection) {
        this.collection = collection;
    }

    public AppliedFilters withCollection(String collection) {
        this.collection = collection;
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

    public AppliedFilters withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(status).append(startDate).append(completionDate).append(lon).append(lat).append(collection).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof AppliedFilters) == false) {
            return false;
        }
        AppliedFilters rhs = ((AppliedFilters) other);
        return new EqualsBuilder().append(status, rhs.status).append(startDate, rhs.startDate).append(completionDate, rhs.completionDate).append(lon, rhs.lon).append(lat, rhs.lat).append(collection, rhs.collection).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
