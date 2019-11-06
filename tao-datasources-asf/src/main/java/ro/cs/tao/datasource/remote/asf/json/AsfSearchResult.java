/*
 * Copyright (C) 2018 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package ro.cs.tao.datasource.remote.asf.json;

import com.fasterxml.jackson.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Valentin Netoiu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "product_file_id",
        "productName",
        "platform",
        "sensor",
        "stringFootprint",
        "downloadUrl",
        "thumbnailUrl",
        "processingDate",
        "sizeMB",
        "fileName",
        "flightDirection",
        "polarization",
        "processingLevel",
        "processingType"

})
public class AsfSearchResult {

    @JsonProperty("product_file_id")
    private String fileId;
    @JsonProperty("productName")
    private String productName;
    @JsonProperty("platform")
    private String platform;
    @JsonProperty("sensor")
    private String sensor;
    @JsonProperty("stringFootprint")
    private String footprint;
    @JsonProperty("downloadUrl")
    private String downloadUrl;
    @JsonProperty("thumbnailUrl")
    private String thumbnailUrl;
    @JsonProperty("browse")
    private String browseUrl;
    @JsonProperty("processingDate")
    private Date processingDate;
    @JsonProperty("sizeMB")
    private String sizeMb;
    @JsonProperty("fileName")
    private String fileName;
    @JsonProperty("flightDirection")
    private String flightDirection;
    @JsonProperty("polarization")
    private String polarisation;
    @JsonProperty("processingLevel")
    private String processingLevel;
    @JsonProperty("processingType")
    private String processingType;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * No args constructor for use in serialization
     *
     */
    public AsfSearchResult() {
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getSensor() {
        return sensor;
    }

    public void setSensor(String sensor) {
        this.sensor = sensor;
    }

    public String getFootprint() {
        return footprint;
    }

    public void setFootprint(String footprint) {
        this.footprint = footprint;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getBrowseUrl() {
        return browseUrl;
    }

    public void setBrowseUrl(String browseUrl) {
        this.browseUrl = browseUrl;
    }

    public Date getProcessingDate() {
        return processingDate;
    }

    public void setProcessingDate(Date processingDate) {
        this.processingDate = processingDate;
    }

    public String getSizeMb() {
        return sizeMb;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFlightDirection() {
        return flightDirection;
    }

    public void setFlightDirection(String flightDirection) {
        this.flightDirection = flightDirection;
    }

    public String getPolarisation() {
        return polarisation;
    }

    public void setPolarisation(String polarisation) {
        this.polarisation = polarisation;
    }

    public String getProcessingLevel() {
        return processingLevel;
    }

    public void setProcessingLevel(String processingLevel) {
        this.processingLevel = processingLevel;
    }

    public String getProcessingType() {
        return processingType;
    }

    public void setProcessingType(String processingType) {
        this.processingType = processingType;
    }

    public void setSizeMb(String sizeMb) {
        this.sizeMb = sizeMb;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}