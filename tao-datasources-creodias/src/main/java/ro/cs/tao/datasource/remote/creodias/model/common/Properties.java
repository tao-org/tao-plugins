
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
    "id",
    "totalResults",
    "exactCount",
    "startIndex",
    "itemsPerPage",
    "query",
    "links"
})
public class Properties {

    @JsonProperty("id")
    private String id;
    @JsonProperty("totalResults")
    private Integer totalResults;
    @JsonProperty("exactCount")
    private Boolean exactCount;
    @JsonProperty("startIndex")
    private Integer startIndex;
    @JsonProperty("itemsPerPage")
    private Integer itemsPerPage;
    @JsonProperty("query")
    private Query query;
    @JsonProperty("links")
    private List<Link> links = new ArrayList<Link>();
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

    public Properties withId(String id) {
        this.id = id;
        return this;
    }

    @JsonProperty("totalResults")
    public Integer getTotalResults() {
        return totalResults;
    }

    @JsonProperty("totalResults")
    public void setTotalResults(Integer totalResults) {
        this.totalResults = totalResults;
    }

    public Properties withTotalResults(Integer totalResults) {
        this.totalResults = totalResults;
        return this;
    }

    @JsonProperty("exactCount")
    public Boolean getExactCount() {
        return exactCount;
    }

    @JsonProperty("exactCount")
    public void setExactCount(Boolean exactCount) {
        this.exactCount = exactCount;
    }

    public Properties withExactCount(Boolean exactCount) {
        this.exactCount = exactCount;
        return this;
    }

    @JsonProperty("startIndex")
    public Integer getStartIndex() {
        return startIndex;
    }

    @JsonProperty("startIndex")
    public void setStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
    }

    public Properties withStartIndex(Integer startIndex) {
        this.startIndex = startIndex;
        return this;
    }

    @JsonProperty("itemsPerPage")
    public Integer getItemsPerPage() {
        return itemsPerPage;
    }

    @JsonProperty("itemsPerPage")
    public void setItemsPerPage(Integer itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }

    public Properties withItemsPerPage(Integer itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
        return this;
    }

    @JsonProperty("query")
    public Query getQuery() {
        return query;
    }

    @JsonProperty("query")
    public void setQuery(Query query) {
        this.query = query;
    }

    public Properties withQuery(Query query) {
        this.query = query;
        return this;
    }

    @JsonProperty("links")
    public List<Link> getLinks() {
        return links;
    }

    @JsonProperty("links")
    public void setLinks(List<Link> links) {
        this.links = links;
    }

    public Properties withLinks(List<Link> links) {
        this.links = links;
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

    public Properties withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(id).append(totalResults).append(exactCount).append(startIndex).append(itemsPerPage).append(query).append(links).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Properties) == false) {
            return false;
        }
        Properties rhs = ((Properties) other);
        return new EqualsBuilder().append(id, rhs.id).append(totalResults, rhs.totalResults).append(exactCount, rhs.exactCount).append(startIndex, rhs.startIndex).append(itemsPerPage, rhs.itemsPerPage).append(query, rhs.query).append(links, rhs.links).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
