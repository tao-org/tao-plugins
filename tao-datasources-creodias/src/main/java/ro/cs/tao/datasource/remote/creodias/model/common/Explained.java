
package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "processor",
    "word"
})
public class Explained {

    @JsonProperty("processor")
    private String processor;
    @JsonProperty("word")
    private String word;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("processor")
    public String getProcessor() {
        return processor;
    }

    @JsonProperty("processor")
    public void setProcessor(String processor) {
        this.processor = processor;
    }

    public Explained withProcessor(String processor) {
        this.processor = processor;
        return this;
    }

    @JsonProperty("word")
    public String getWord() {
        return word;
    }

    @JsonProperty("word")
    public void setWord(String word) {
        this.word = word;
    }

    public Explained withWord(String word) {
        this.word = word;
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

    public Explained withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(processor).append(word).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Explained) == false) {
            return false;
        }
        Explained rhs = ((Explained) other);
        return new EqualsBuilder().append(processor, rhs.processor).append(word, rhs.word).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
