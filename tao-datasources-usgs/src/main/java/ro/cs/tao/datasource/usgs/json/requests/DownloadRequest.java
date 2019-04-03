package ro.cs.tao.datasource.usgs.json.requests;

public class DownloadRequest extends BaseRequest {
    private String[] entityIds;
    private String products = "STANDARD";

    public String[] getEntityIds() {
        return entityIds;
    }

    public void setEntityIds(String[] entityIds) {
        this.entityIds = entityIds;
    }

    public String getProducts() {
        return products;
    }

    public void setProducts(String products) {
        this.products = products;
    }
}
