
package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "name",
    "normalized",
    "type",
    "href",
    "parentHash",
    "value",
    "gcover",
    "area"
})
public class Keyword {

    @JsonProperty("id")
    private String id;
    @JsonProperty("name")
    private String name;
    @JsonProperty("normalized")
    private String normalized;
    @JsonProperty("type")
    private String type;
    @JsonProperty("href")
    private String href;
    @JsonProperty("parentHash")
    private String parentHash;
    @JsonProperty("value")
    private Double value;
    @JsonProperty("gcover")
    private Double gcover;
    @JsonProperty("area")
    private Double area;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public Keyword withId(String id) {
        this.id = id;
        return this;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public Keyword withName(String name) {
        this.name = name;
        return this;
    }

    @JsonProperty("normalized")
    public String getNormalized() {
        return normalized;
    }

    @JsonProperty("normalized")
    public void setNormalized(String normalized) {
        this.normalized = normalized;
    }

    public Keyword withNormalized(String normalized) {
        this.normalized = normalized;
        return this;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    public Keyword withType(String type) {
        this.type = type;
        return this;
    }

    @JsonProperty("href")
    public String getHref() {
        return href;
    }

    @JsonProperty("href")
    public void setHref(String href) {
        this.href = href;
    }

    public Keyword withHref(String href) {
        this.href = href;
        return this;
    }

    @JsonProperty("parentHash")
    public String getParentHash() {
        return parentHash;
    }

    @JsonProperty("parentHash")
    public void setParentHash(String parentHash) {
        this.parentHash = parentHash;
    }

    public Keyword withParentHash(String parentHash) {
        this.parentHash = parentHash;
        return this;
    }

    @JsonProperty("value")
    public Double getValue() {
        return value;
    }

    @JsonProperty("value")
    public void setValue(Double value) {
        this.value = value;
    }

    public Keyword withValue(Double value) {
        this.value = value;
        return this;
    }

    @JsonProperty("gcover")
    public Double getGcover() {
        return gcover;
    }

    @JsonProperty("gcover")
    public void setGcover(Double gcover) {
        this.gcover = gcover;
    }

    public Keyword withGcover(Double gcover) {
        this.gcover = gcover;
        return this;
    }

    @JsonProperty("area")
    public Double getArea() {
        return area;
    }

    @JsonProperty("area")
    public void setArea(Double area) {
        this.area = area;
    }

    public Keyword withArea(Double area) {
        this.area = area;
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

    public Keyword withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(id).append(name).append(normalized).append(type).append(href).append(parentHash).append(value).append(gcover).append(area).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Keyword) == false) {
            return false;
        }
        Keyword rhs = ((Keyword) other);
        return new EqualsBuilder().append(id, rhs.id).append(name, rhs.name).append(normalized, rhs.normalized).append(type, rhs.type).append(href, rhs.href).append(parentHash, rhs.parentHash).append(value, rhs.value).append(gcover, rhs.gcover).append(area, rhs.area).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
