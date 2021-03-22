package ro.cs.tao.datasource.usgs.json.responses;

import java.util.List;

public class DownloadInfo {
    private String id;
    private String displayId;
    private String entityId;
    private String datasetId;
    private String available;
    private long filesize;
    private String productName;
    private String productCode;
    private String bulkAvailable;
    private String downloadSystem;
    private List<DownloadInfo> secondaryDownloads;

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayId() {
        return displayId;
    }

    public void setDisplayId(String displayId) {
        this.displayId = displayId;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getAvailable() {
        return available;
    }

    public void setAvailable(String available) {
        this.available = available;
    }

    public long getFilesize() {
        return filesize;
    }

    public void setFilesize(long filesize) {
        this.filesize = filesize;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getBulkAvailable() {
        return bulkAvailable;
    }

    public void setBulkAvailable(String bulkAvailable) {
        this.bulkAvailable = bulkAvailable;
    }

    public String getDownloadSystem() {
        return downloadSystem;
    }

    public void setDownloadSystem(String downloadSystem) {
        this.downloadSystem = downloadSystem;
    }

    public List<DownloadInfo> getSecondaryDownloads() {
        return secondaryDownloads;
    }

    public void setSecondaryDownloads(List<DownloadInfo> secondaryDownloads) {
        this.secondaryDownloads = secondaryDownloads;
    }
}
