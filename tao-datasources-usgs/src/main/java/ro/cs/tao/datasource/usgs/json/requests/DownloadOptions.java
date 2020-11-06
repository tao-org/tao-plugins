package ro.cs.tao.datasource.usgs.json.requests;

public class DownloadOptions extends BaseRequest {
    private String datasetName;
    private String entityIds;

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getEntityIds() {
        return entityIds;
    }

    public void setEntityIds(String entityIds) {
        this.entityIds = entityIds;
    }
}
