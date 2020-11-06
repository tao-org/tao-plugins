package ro.cs.tao.datasource.usgs.json.types;

public class Download {
    private String entityId;
    private String productId;
    private String dataUse;
    private String label;

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getDataUse() {
        return dataUse;
    }

    public void setDataUse(String dataUse) {
        this.dataUse = dataUse;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
