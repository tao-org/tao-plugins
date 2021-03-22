package ro.cs.tao.datasource.usgs.json.responses;

import java.util.List;

public class DownloadResponseData {
    private List<AvailableDownload> failed;
    //private Map<String, String> newRecords;
    private Object newRecords;
    private int numInvalidScenes;
    //private Map<String, String> duplicateProducts;
    private Object duplicateProducts;
    private List<AvailableDownload> availableDownloads;
    private List<AvailableDownload> preparingDownloads;

    public List<AvailableDownload> getFailed() {
        return failed;
    }

    public void setFailed(List<AvailableDownload> failed) {
        this.failed = failed;
    }

    public Object getNewRecords() {
        return newRecords;
    }

    public void setNewRecords(Object newRecords) {
        this.newRecords = newRecords;
    }

    /*public Map<String, String> getNewRecords() {
        return newRecords;
    }

    public void setNewRecords(Map<String, String> newRecords) {
        this.newRecords = newRecords;
    }*/

    public int getNumInvalidScenes() {
        return numInvalidScenes;
    }

    public void setNumInvalidScenes(int numInvalidScenes) {
        this.numInvalidScenes = numInvalidScenes;
    }

    public Object getDuplicateProducts() {
        return duplicateProducts;
    }

    public void setDuplicateProducts(Object duplicateProducts) {
        this.duplicateProducts = duplicateProducts;
    }

    /*public Map<String, String>  getDuplicateProducts() {
        return duplicateProducts;
    }

    public void setDuplicateProducts(Map<String, String>  duplicateProducts) {
        this.duplicateProducts = duplicateProducts;
    }*/

    public List<AvailableDownload> getAvailableDownloads() {
        return availableDownloads;
    }

    public void setAvailableDownloads(List<AvailableDownload> availableDownloads) {
        this.availableDownloads = availableDownloads;
    }

    public List<AvailableDownload> getPreparingDownloads() {
        return preparingDownloads;
    }

    public void setPreparingDownloads(List<AvailableDownload> preparingDownloads) {
        this.preparingDownloads = preparingDownloads;
    }
}
