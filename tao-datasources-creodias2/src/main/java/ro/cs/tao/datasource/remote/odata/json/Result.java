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
package ro.cs.tao.datasource.remote.odata.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Cosmin Cara
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result {

    @JsonProperty("@odata.mediaContentType")
    private String oDataMediaContentType;
    @JsonProperty("Id")
    private String id;
    @JsonProperty("Name")
    private String name;
    @JsonProperty("ContentType")
    private String contentType;
    @JsonProperty("ContentLength")
    private long contentLength;
    @JsonProperty("OriginDate")
    private LocalDateTime originDate;
    @JsonProperty("PublicationDate")
    private LocalDateTime publicationDate;
    @JsonProperty("ModificationDate")
    private LocalDateTime modificationDate;
    @JsonProperty("Online")
    private boolean online;
    @JsonProperty("EvictionDate")
    private String evictionDate;
    @JsonProperty("S3Path")
    private String s3Path;
    @JsonProperty("Checksum")
    private Object checksum;
    @JsonProperty("ContentDate")
    private ContentDate contentDate;
    @JsonProperty("Footprint")
    private String footprint;
    @JsonProperty("GeoFootprint")
    private GeoFootprint geoFootprint;
    @JsonProperty("Attributes")
    private List<Attribute> attributes;
    @JsonProperty("Assets")
    private List<Asset> assets;

    /**
     * No args constructor for use in serialization
     *
     */
    public Result() {
    }

    public String getoDataMediaContentType() {
        return oDataMediaContentType;
    }

    public void setoDataMediaContentType(String oDataMediaContentType) {
        this.oDataMediaContentType = oDataMediaContentType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public LocalDateTime getOriginDate() {
        return originDate;
    }

    public void setOriginDate(LocalDateTime originDate) {
        this.originDate = originDate;
    }

    public LocalDateTime getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(LocalDateTime publicationDate) {
        this.publicationDate = publicationDate;
    }

    public LocalDateTime getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(LocalDateTime modificationDate) {
        this.modificationDate = modificationDate;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getEvictionDate() {
        return evictionDate;
    }

    public void setEvictionDate(String evictionDate) {
        this.evictionDate = evictionDate;
    }

    public String getS3Path() {
        return s3Path;
    }

    public void setS3Path(String s3Path) {
        this.s3Path = s3Path;
    }

    public Object getChecksum() {
        return checksum;
    }

    public void setChecksum(Object checksum) {
        this.checksum = checksum;
    }

    public ContentDate getContentDate() {
        return contentDate;
    }

    public void setContentDate(ContentDate contentDate) {
        this.contentDate = contentDate;
    }

    public String getFootprint() {
        return footprint;
    }

    public void setFootprint(String footprint) {
        this.footprint = footprint;
    }

    public GeoFootprint getGeoFootprint() {
        return geoFootprint;
    }

    public void setGeoFootprint(GeoFootprint geoFootprint) {
        this.geoFootprint = geoFootprint;
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }

    public List<Asset> getAssets() {
        return assets;
    }

    public void setAssets(List<Asset> assets) {
        this.assets = assets;
    }
}