
package ro.cs.tao.datasource.remote.creodias.model.s1;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import ro.cs.tao.datasource.remote.creodias.model.common.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "collection",
    "status",
    "license",
    "productIdentifier",
    "parentIdentifier",
    "title",
    "description",
    "organisationName",
    "startDate",
    "completionDate",
    "productType",
    "processingLevel",
    "platform",
    "instrument",
    "resolution",
    "sensorMode",
    "orbitNumber",
    "quicklook",
    "thumbnail",
    "updated",
    "published",
    "snowCover",
    "cloudCover",
    "keywords",
    "centroid",
    "orbitDirection",
    "missionTakeId",
    "polarisation",
    "swath",
    "services",
    "links"
})
public class Result {

    @JsonProperty("collection")
    private String collection;
    @JsonProperty("status")
    private Integer status;
    @JsonProperty("license")
    private License license;
    @JsonProperty("productIdentifier")
    private String productIdentifier;
    @JsonProperty("parentIdentifier")
    private Object parentIdentifier;
    @JsonProperty("title")
    private String title;
    @JsonProperty("description")
    private Object description;
    @JsonProperty("organisationName")
    private String organisationName;
    @JsonProperty("startDate")
    private String startDate;
    @JsonProperty("completionDate")
    private String completionDate;
    @JsonProperty("productType")
    private String productType;
    @JsonProperty("processingLevel")
    private String processingLevel;
    @JsonProperty("platform")
    private String platform;
    @JsonProperty("instrument")
    private String instrument;
    @JsonProperty("resolution")
    private Double resolution;
    @JsonProperty("sensorMode")
    private String sensorMode;
    @JsonProperty("orbitNumber")
    private Integer orbitNumber;
    @JsonProperty("quicklook")
    private Object quicklook;
    @JsonProperty("thumbnail")
    private String thumbnail;
    @JsonProperty("updated")
    private String updated;
    @JsonProperty("published")
    private String published;
    @JsonProperty("snowCover")
    private Integer snowCover;
    @JsonProperty("cloudCover")
    private Integer cloudCover;
    @JsonProperty("keywords")
    private List<Keyword> keywords = new ArrayList<>();
    @JsonProperty("centroid")
    private Centroid centroid;
    @JsonProperty("orbitDirection")
    private String orbitDirection;
    @JsonProperty("missionTakeId")
    private Integer missionTakeId;
    @JsonProperty("polarisation")
    private String polarisation;
    @JsonProperty("swath")
    private String swath;
    @JsonProperty("services")
    private Services services;
    @JsonProperty("links")
    private List<Link> links = new ArrayList<>();
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("collection")
    public String getCollection() {
        return collection;
    }

    @JsonProperty("collection")
    public void setCollection(String collection) {
        this.collection = collection;
    }

    public Result withCollection(String collection) {
        this.collection = collection;
        return this;
    }

    @JsonProperty("status")
    public Integer getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    public Result withStatus(Integer status) {
        this.status = status;
        return this;
    }

    @JsonProperty("license")
    public License getLicense() {
        return license;
    }

    @JsonProperty("license")
    public void setLicense(License license) {
        this.license = license;
    }

    public Result withLicense(License license) {
        this.license = license;
        return this;
    }

    @JsonProperty("productIdentifier")
    public String getProductIdentifier() {
        return productIdentifier;
    }

    @JsonProperty("productIdentifier")
    public void setProductIdentifier(String productIdentifier) {
        this.productIdentifier = productIdentifier;
    }

    public Result withProductIdentifier(String productIdentifier) {
        this.productIdentifier = productIdentifier;
        return this;
    }

    @JsonProperty("parentIdentifier")
    public Object getParentIdentifier() {
        return parentIdentifier;
    }

    @JsonProperty("parentIdentifier")
    public void setParentIdentifier(Object parentIdentifier) {
        this.parentIdentifier = parentIdentifier;
    }

    public Result withParentIdentifier(Object parentIdentifier) {
        this.parentIdentifier = parentIdentifier;
        return this;
    }

    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    @JsonProperty("title")
    public void setTitle(String title) {
        this.title = title;
    }

    public Result withTitle(String title) {
        this.title = title;
        return this;
    }

    @JsonProperty("description")
    public Object getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(Object description) {
        this.description = description;
    }

    public Result withDescription(Object description) {
        this.description = description;
        return this;
    }

    @JsonProperty("organisationName")
    public String getOrganisationName() {
        return organisationName;
    }

    @JsonProperty("organisationName")
    public void setOrganisationName(String organisationName) {
        this.organisationName = organisationName;
    }

    public Result withOrganisationName(String organisationName) {
        this.organisationName = organisationName;
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

    public Result withStartDate(String startDate) {
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

    public Result withCompletionDate(String completionDate) {
        this.completionDate = completionDate;
        return this;
    }

    @JsonProperty("productType")
    public String getProductType() {
        return productType;
    }

    @JsonProperty("productType")
    public void setProductType(String productType) {
        this.productType = productType;
    }

    public Result withProductType(String productType) {
        this.productType = productType;
        return this;
    }

    @JsonProperty("processingLevel")
    public String getProcessingLevel() {
        return processingLevel;
    }

    @JsonProperty("processingLevel")
    public void setProcessingLevel(String processingLevel) {
        this.processingLevel = processingLevel;
    }

    public Result withProcessingLevel(String processingLevel) {
        this.processingLevel = processingLevel;
        return this;
    }

    @JsonProperty("platform")
    public String getPlatform() {
        return platform;
    }

    @JsonProperty("platform")
    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public Result withPlatform(String platform) {
        this.platform = platform;
        return this;
    }

    @JsonProperty("instrument")
    public String getInstrument() {
        return instrument;
    }

    @JsonProperty("instrument")
    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public Result withInstrument(String instrument) {
        this.instrument = instrument;
        return this;
    }

    @JsonProperty("resolution")
    public Double getResolution() {
        return resolution;
    }

    @JsonProperty("resolution")
    public void setResolution(Double resolution) {
        this.resolution = resolution;
    }

    public Result withResolution(Double resolution) {
        this.resolution = resolution;
        return this;
    }

    @JsonProperty("sensorMode")
    public String getSensorMode() {
        return sensorMode;
    }

    @JsonProperty("sensorMode")
    public void setSensorMode(String sensorMode) {
        this.sensorMode = sensorMode;
    }

    public Result withSensorMode(String sensorMode) {
        this.sensorMode = sensorMode;
        return this;
    }

    @JsonProperty("orbitNumber")
    public Integer getOrbitNumber() {
        return orbitNumber;
    }

    @JsonProperty("orbitNumber")
    public void setOrbitNumber(Integer orbitNumber) {
        this.orbitNumber = orbitNumber;
    }

    public Result withOrbitNumber(Integer orbitNumber) {
        this.orbitNumber = orbitNumber;
        return this;
    }

    @JsonProperty("quicklook")
    public Object getQuicklook() {
        return quicklook;
    }

    @JsonProperty("quicklook")
    public void setQuicklook(Object quicklook) {
        this.quicklook = quicklook;
    }

    public Result withQuicklook(Object quicklook) {
        this.quicklook = quicklook;
        return this;
    }

    @JsonProperty("thumbnail")
    public String getThumbnail() {
        return thumbnail;
    }

    @JsonProperty("thumbnail")
    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public Result withThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
        return this;
    }

    @JsonProperty("updated")
    public String getUpdated() {
        return updated;
    }

    @JsonProperty("updated")
    public void setUpdated(String updated) {
        this.updated = updated;
    }

    public Result withUpdated(String updated) {
        this.updated = updated;
        return this;
    }

    @JsonProperty("published")
    public String getPublished() {
        return published;
    }

    @JsonProperty("published")
    public void setPublished(String published) {
        this.published = published;
    }

    public Result withPublished(String published) {
        this.published = published;
        return this;
    }

    @JsonProperty("snowCover")
    public Integer getSnowCover() {
        return snowCover;
    }

    @JsonProperty("snowCover")
    public void setSnowCover(Integer snowCover) {
        this.snowCover = snowCover;
    }

    public Result withSnowCover(Integer snowCover) {
        this.snowCover = snowCover;
        return this;
    }

    @JsonProperty("cloudCover")
    public Integer getCloudCover() {
        return cloudCover;
    }

    @JsonProperty("cloudCover")
    public void setCloudCover(Integer cloudCover) {
        this.cloudCover = cloudCover;
    }

    public Result withCloudCover(Integer cloudCover) {
        this.cloudCover = cloudCover;
        return this;
    }

    @JsonProperty("keywords")
    public List<Keyword> getKeywords() {
        return keywords;
    }

    @JsonProperty("keywords")
    public void setKeywords(List<Keyword> keywords) {
        this.keywords = keywords;
    }

    public Result withKeywords(List<Keyword> keywords) {
        this.keywords = keywords;
        return this;
    }

    @JsonProperty("centroid")
    public Centroid getCentroid() {
        return centroid;
    }

    @JsonProperty("centroid")
    public void setCentroid(Centroid centroid) {
        this.centroid = centroid;
    }

    public Result withCentroid(Centroid centroid) {
        this.centroid = centroid;
        return this;
    }

    @JsonProperty("orbitDirection")
    public String getOrbitDirection() {
        return orbitDirection;
    }

    @JsonProperty("orbitDirection")
    public void setOrbitDirection(String orbitDirection) {
        this.orbitDirection = orbitDirection;
    }

    public Result withOrbitDirection(String orbitDirection) {
        this.orbitDirection = orbitDirection;
        return this;
    }

    @JsonProperty("missionTakeId")
    public Integer getMissionTakeId() {
        return missionTakeId;
    }

    @JsonProperty("missionTakeId")
    public void setMissionTakeId(Integer missionTakeId) {
        this.missionTakeId = missionTakeId;
    }

    public Result withMissionTakeId(Integer missionTakeId) {
        this.missionTakeId = missionTakeId;
        return this;
    }

    @JsonProperty("polarisation")
    public String getPolarisation() {
        return polarisation;
    }

    @JsonProperty("polarisation")
    public void setPolarisation(String polarisation) {
        this.polarisation = polarisation;
    }

    public Result withPolarisation(String polarisation) {
        this.polarisation = polarisation;
        return this;
    }

    @JsonProperty("swath")
    public String getSwath() {
        return swath;
    }

    @JsonProperty("swath")
    public void setSwath(String swath) {
        this.swath = swath;
    }

    public Result withSwath(String swath) {
        this.swath = swath;
        return this;
    }

    @JsonProperty("services")
    public Services getServices() {
        return services;
    }

    @JsonProperty("services")
    public void setServices(Services services) {
        this.services = services;
    }

    public Result withServices(Services services) {
        this.services = services;
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

    public Result withLinks(List<Link> links) {
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

    public Result withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(collection).append(status).append(license).append(productIdentifier).append(parentIdentifier).append(title).append(description).append(organisationName).append(startDate).append(completionDate).append(productType).append(processingLevel).append(platform).append(instrument).append(resolution).append(sensorMode).append(orbitNumber).append(quicklook).append(thumbnail).append(updated).append(published).append(snowCover).append(cloudCover).append(keywords).append(centroid).append(orbitDirection).append(missionTakeId).append(polarisation).append(swath).append(services).append(links).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof Result) == false) {
            return false;
        }
        Result rhs = ((Result) other);
        return new EqualsBuilder().append(collection, rhs.collection).append(status, rhs.status).append(license, rhs.license).append(productIdentifier, rhs.productIdentifier).append(parentIdentifier, rhs.parentIdentifier).append(title, rhs.title).append(description, rhs.description).append(organisationName, rhs.organisationName).append(startDate, rhs.startDate).append(completionDate, rhs.completionDate).append(productType, rhs.productType).append(processingLevel, rhs.processingLevel).append(platform, rhs.platform).append(instrument, rhs.instrument).append(resolution, rhs.resolution).append(sensorMode, rhs.sensorMode).append(orbitNumber, rhs.orbitNumber).append(quicklook, rhs.quicklook).append(thumbnail, rhs.thumbnail).append(updated, rhs.updated).append(published, rhs.published).append(snowCover, rhs.snowCover).append(cloudCover, rhs.cloudCover).append(keywords, rhs.keywords).append(centroid, rhs.centroid).append(orbitDirection, rhs.orbitDirection).append(missionTakeId, rhs.missionTakeId).append(polarisation, rhs.polarisation).append(swath, rhs.swath).append(services, rhs.services).append(links, rhs.links).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
