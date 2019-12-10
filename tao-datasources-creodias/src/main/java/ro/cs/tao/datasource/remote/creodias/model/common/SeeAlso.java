
package ro.cs.tao.datasource.remote.creodias.model.common;

import com.fasterxml.jackson.annotation.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "type",
    "country",
    "geo:lon",
    "geo:lat",
    "ccode",
    "fcode",
    "admin1",
    "admin2",
    "population",
    "elevation",
    "gtopo30",
    "timezone"
})
public class SeeAlso {

    @JsonProperty("name")
    private String name;
    @JsonProperty("type")
    private String type;
    @JsonProperty("country")
    private String country;
    @JsonProperty("geo:lon")
    private Double geoLon;
    @JsonProperty("geo:lat")
    private Double geoLat;
    @JsonProperty("ccode")
    private String ccode;
    @JsonProperty("fcode")
    private String fcode;
    @JsonProperty("admin1")
    private String admin1;
    @JsonProperty("admin2")
    private String admin2;
    @JsonProperty("population")
    private Integer population;
    @JsonProperty("elevation")
    private Integer elevation;
    @JsonProperty("gtopo30")
    private String gtopo30;
    @JsonProperty("timezone")
    private String timezone;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public SeeAlso withName(String name) {
        this.name = name;
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

    public SeeAlso withType(String type) {
        this.type = type;
        return this;
    }

    @JsonProperty("country")
    public String getCountry() {
        return country;
    }

    @JsonProperty("country")
    public void setCountry(String country) {
        this.country = country;
    }

    public SeeAlso withCountry(String country) {
        this.country = country;
        return this;
    }

    @JsonProperty("geo:lon")
    public Double getGeoLon() {
        return geoLon;
    }

    @JsonProperty("geo:lon")
    public void setGeoLon(Double geoLon) {
        this.geoLon = geoLon;
    }

    public SeeAlso withGeoLon(Double geoLon) {
        this.geoLon = geoLon;
        return this;
    }

    @JsonProperty("geo:lat")
    public Double getGeoLat() {
        return geoLat;
    }

    @JsonProperty("geo:lat")
    public void setGeoLat(Double geoLat) {
        this.geoLat = geoLat;
    }

    public SeeAlso withGeoLat(Double geoLat) {
        this.geoLat = geoLat;
        return this;
    }

    @JsonProperty("ccode")
    public String getCcode() {
        return ccode;
    }

    @JsonProperty("ccode")
    public void setCcode(String ccode) {
        this.ccode = ccode;
    }

    public SeeAlso withCcode(String ccode) {
        this.ccode = ccode;
        return this;
    }

    @JsonProperty("fcode")
    public String getFcode() {
        return fcode;
    }

    @JsonProperty("fcode")
    public void setFcode(String fcode) {
        this.fcode = fcode;
    }

    public SeeAlso withFcode(String fcode) {
        this.fcode = fcode;
        return this;
    }

    @JsonProperty("admin1")
    public String getAdmin1() {
        return admin1;
    }

    @JsonProperty("admin1")
    public void setAdmin1(String admin1) {
        this.admin1 = admin1;
    }

    public SeeAlso withAdmin1(String admin1) {
        this.admin1 = admin1;
        return this;
    }

    @JsonProperty("admin2")
    public String getAdmin2() {
        return admin2;
    }

    @JsonProperty("admin2")
    public void setAdmin2(String admin2) {
        this.admin2 = admin2;
    }

    public SeeAlso withAdmin2(String admin2) {
        this.admin2 = admin2;
        return this;
    }

    @JsonProperty("population")
    public Integer getPopulation() {
        return population;
    }

    @JsonProperty("population")
    public void setPopulation(Integer population) {
        this.population = population;
    }

    public SeeAlso withPopulation(Integer population) {
        this.population = population;
        return this;
    }

    @JsonProperty("elevation")
    public Integer getElevation() {
        return elevation;
    }

    @JsonProperty("elevation")
    public void setElevation(Integer elevation) {
        this.elevation = elevation;
    }

    public SeeAlso withElevation(Integer elevation) {
        this.elevation = elevation;
        return this;
    }

    @JsonProperty("gtopo30")
    public String getGtopo30() {
        return gtopo30;
    }

    @JsonProperty("gtopo30")
    public void setGtopo30(String gtopo30) {
        this.gtopo30 = gtopo30;
    }

    public SeeAlso withGtopo30(String gtopo30) {
        this.gtopo30 = gtopo30;
        return this;
    }

    @JsonProperty("timezone")
    public String getTimezone() {
        return timezone;
    }

    @JsonProperty("timezone")
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public SeeAlso withTimezone(String timezone) {
        this.timezone = timezone;
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

    public SeeAlso withAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(name).append(type).append(country).append(geoLon).append(geoLat).append(ccode).append(fcode).append(admin1).append(admin2).append(population).append(elevation).append(gtopo30).append(timezone).append(additionalProperties).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof SeeAlso) == false) {
            return false;
        }
        SeeAlso rhs = ((SeeAlso) other);
        return new EqualsBuilder().append(name, rhs.name).append(type, rhs.type).append(country, rhs.country).append(geoLon, rhs.geoLon).append(geoLat, rhs.geoLat).append(ccode, rhs.ccode).append(fcode, rhs.fcode).append(admin1, rhs.admin1).append(admin2, rhs.admin2).append(population, rhs.population).append(elevation, rhs.elevation).append(gtopo30, rhs.gtopo30).append(timezone, rhs.timezone).append(additionalProperties, rhs.additionalProperties).isEquals();
    }

}
