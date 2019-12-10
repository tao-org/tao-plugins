
package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "What",
    "When",
    "Where",
    "Errors",
    "Explained"
})
public class Analyze {

    @JsonProperty("What")
    private List<Object> what = new ArrayList<Object>();
    @JsonProperty("When")
    private List<When> when;
    @JsonProperty("Where")
    private List<Where> where = new ArrayList<Where>();
    @JsonProperty("Errors")
    private List<Object> errors = new ArrayList<Object>();
    @JsonProperty("Explained")
    private List<Explained> explained = new ArrayList<Explained>();
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("What")
    public List<Object> getWhat() {
        return what;
    }

    @JsonProperty("What")
    public void setWhat(List<Object> what) {
        this.what = what;
    }

    public Analyze withWhat(List<Object> what) {
        this.what = what;
        return this;
    }

    @JsonProperty("When")
    public List<When> getWhen() {
        return when;
    }

    @JsonProperty("When")
    public void setWhen(List<When> when) {
        this.when = when;
    }

    public Analyze withWhen(List<When> when) {
        this.when = when;
        return this;
    }

    @JsonProperty("Where")
    public List<Where> getWhere() {
        return where;
    }

    @JsonProperty("Where")
    public void setWhere(List<Where> where) {
        this.where = where;
    }

    public Analyze withWhere(List<Where> where) {
        this.where = where;
        return this;
    }

    @JsonProperty("Errors")
    public List<Object> getErrors() {
        return errors;
    }

    @JsonProperty("Errors")
    public void setErrors(List<Object> errors) {
        this.errors = errors;
    }

    public Analyze withErrors(List<Object> errors) {
        this.errors = errors;
        return this;
    }

    @JsonProperty("Explained")
    public List<Explained> getExplained() {
        return explained;
    }

    @JsonProperty("Explained")
    public void setExplained(List<Explained> explained) {
        this.explained = explained;
    }

    public Analyze withExplained(List<Explained> explained) {
        this.explained = explained;
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

    public Analyze withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(what).append(when).append(where).append(errors).append(explained).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Analyze) == false) {
            return false;
        }
        Analyze rhs = ((Analyze) other);
        return new EqualsBuilder().append(what, rhs.what).append(when, rhs.when).append(where, rhs.where).append(errors, rhs.errors).append(explained, rhs.explained).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
