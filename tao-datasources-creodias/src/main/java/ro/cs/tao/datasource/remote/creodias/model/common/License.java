
package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "licenseId",
    "hasToBeSigned",
    "grantedCountries",
    "grantedOrganizationCountries",
    "grantedFlags",
    "viewService",
    "signatureQuota",
    "description"
})
public class License {

    @JsonProperty("licenseId")
    private String licenseId;
    @JsonProperty("hasToBeSigned")
    private String hasToBeSigned;
    @JsonProperty("grantedCountries")
    private Object grantedCountries;
    @JsonProperty("grantedOrganizationCountries")
    private Object grantedOrganizationCountries;
    @JsonProperty("grantedFlags")
    private Object grantedFlags;
    @JsonProperty("viewService")
    private String viewService;
    @JsonProperty("signatureQuota")
    private Integer signatureQuota;
    @JsonProperty("description")
    private Description description;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("licenseId")
    public String getLicenseId() {
        return licenseId;
    }

    @JsonProperty("licenseId")
    public void setLicenseId(String licenseId) {
        this.licenseId = licenseId;
    }

    public License withLicenseId(String licenseId) {
        this.licenseId = licenseId;
        return this;
    }

    @JsonProperty("hasToBeSigned")
    public String getHasToBeSigned() {
        return hasToBeSigned;
    }

    @JsonProperty("hasToBeSigned")
    public void setHasToBeSigned(String hasToBeSigned) {
        this.hasToBeSigned = hasToBeSigned;
    }

    public License withHasToBeSigned(String hasToBeSigned) {
        this.hasToBeSigned = hasToBeSigned;
        return this;
    }

    @JsonProperty("grantedCountries")
    public Object getGrantedCountries() {
        return grantedCountries;
    }

    @JsonProperty("grantedCountries")
    public void setGrantedCountries(Object grantedCountries) {
        this.grantedCountries = grantedCountries;
    }

    public License withGrantedCountries(Object grantedCountries) {
        this.grantedCountries = grantedCountries;
        return this;
    }

    @JsonProperty("grantedOrganizationCountries")
    public Object getGrantedOrganizationCountries() {
        return grantedOrganizationCountries;
    }

    @JsonProperty("grantedOrganizationCountries")
    public void setGrantedOrganizationCountries(Object grantedOrganizationCountries) {
        this.grantedOrganizationCountries = grantedOrganizationCountries;
    }

    public License withGrantedOrganizationCountries(Object grantedOrganizationCountries) {
        this.grantedOrganizationCountries = grantedOrganizationCountries;
        return this;
    }

    @JsonProperty("grantedFlags")
    public Object getGrantedFlags() {
        return grantedFlags;
    }

    @JsonProperty("grantedFlags")
    public void setGrantedFlags(Object grantedFlags) {
        this.grantedFlags = grantedFlags;
    }

    public License withGrantedFlags(Object grantedFlags) {
        this.grantedFlags = grantedFlags;
        return this;
    }

    @JsonProperty("viewService")
    public String getViewService() {
        return viewService;
    }

    @JsonProperty("viewService")
    public void setViewService(String viewService) {
        this.viewService = viewService;
    }

    public License withViewService(String viewService) {
        this.viewService = viewService;
        return this;
    }

    @JsonProperty("signatureQuota")
    public Integer getSignatureQuota() {
        return signatureQuota;
    }

    @JsonProperty("signatureQuota")
    public void setSignatureQuota(Integer signatureQuota) {
        this.signatureQuota = signatureQuota;
    }

    public License withSignatureQuota(Integer signatureQuota) {
        this.signatureQuota = signatureQuota;
        return this;
    }

    @JsonProperty("description")
    public Description getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(Description description) {
        this.description = description;
    }

    public License withDescription(Description description) {
        this.description = description;
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

    public License withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(licenseId).append(hasToBeSigned).append(grantedCountries).append(grantedOrganizationCountries).append(grantedFlags).append(viewService).append(signatureQuota).append(description).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof License) == false) {
            return false;
        }
        License rhs = ((License) other);
        return new EqualsBuilder().append(licenseId, rhs.licenseId).append(hasToBeSigned, rhs.hasToBeSigned).append(grantedCountries, rhs.grantedCountries).append(grantedOrganizationCountries, rhs.grantedOrganizationCountries).append(grantedFlags, rhs.grantedFlags).append(viewService, rhs.viewService).append(signatureQuota, rhs.signatureQuota).append(description, rhs.description).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
