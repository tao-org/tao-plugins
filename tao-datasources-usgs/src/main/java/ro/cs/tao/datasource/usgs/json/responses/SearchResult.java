package ro.cs.tao.datasource.usgs.json.responses;

import java.time.LocalDate;

public class SearchResult {
    private LocalDate acquisitionDate;
    private LocalDate startTime;
    private LocalDate endTime;
    private SpatialFootprint spatialFootprint;
    private String sceneBounds;
    private String browseUrl;
    private String dataAccessUrl;
    private String downloadUrl;
    private String entityId;
    private String displayId;
    private Double cloudCover;
    private String metadataUrl;
    private String fgdcMetadataUrl;
    private LocalDate modifiedDate;
    private String orderUrl;
    private boolean bulkOrdered;
    private boolean ordered;
    private String summary;

    public LocalDate getAcquisitionDate() {
        return acquisitionDate;
    }

    public void setAcquisitionDate(LocalDate acquisitionDate) {
        this.acquisitionDate = acquisitionDate;
    }

    public LocalDate getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDate startTime) {
        this.startTime = startTime;
    }

    public LocalDate getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDate endTime) {
        this.endTime = endTime;
    }

    public SpatialFootprint getSpatialFootprint() {
        return spatialFootprint;
    }

    public void setSpatialFootprint(SpatialFootprint spatialFootprint) {
        this.spatialFootprint = spatialFootprint;
    }

    public String getSceneBounds() {
        return sceneBounds;
    }

    public void setSceneBounds(String sceneBounds) {
        this.sceneBounds = sceneBounds;
    }

    public String getBrowseUrl() {
        return browseUrl;
    }

    public void setBrowseUrl(String browseUrl) {
        this.browseUrl = browseUrl;
    }

    public String getDataAccessUrl() {
        return dataAccessUrl;
    }

    public void setDataAccessUrl(String dataAccessUrl) {
        this.dataAccessUrl = dataAccessUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDisplayId() {
        return displayId;
    }

    public void setDisplayId(String displayId) {
        this.displayId = displayId;
    }

    public Double getCloudCover() { return cloudCover; }

    public void setCloudCover(Double cloudCover) { this.cloudCover = cloudCover; }

    public String getMetadataUrl() {
        return metadataUrl;
    }

    public void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl;
    }

    public String getFgdcMetadataUrl() {
        return fgdcMetadataUrl;
    }

    public void setFgdcMetadataUrl(String fgdcMetadataUrl) {
        this.fgdcMetadataUrl = fgdcMetadataUrl;
    }

    public LocalDate getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getOrderUrl() {
        return orderUrl;
    }

    public void setOrderUrl(String orderUrl) {
        this.orderUrl = orderUrl;
    }

    public boolean isBulkOrdered() {
        return bulkOrdered;
    }

    public void setBulkOrdered(boolean bulkOrdered) {
        this.bulkOrdered = bulkOrdered;
    }

    public boolean isOrdered() { return ordered; }

    public void setOrdered(boolean ordered) { this.ordered = ordered; }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
