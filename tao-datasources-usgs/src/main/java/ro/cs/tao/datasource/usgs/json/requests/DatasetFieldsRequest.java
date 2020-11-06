package ro.cs.tao.datasource.usgs.json.requests;

public class DatasetFieldsRequest extends BaseRequest {
    private String datasetName;

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

}
