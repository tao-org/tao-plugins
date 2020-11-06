package ro.cs.tao.datasource.usgs.json.types;

public class MetadataValue extends MetadataFilter {
    private String filterId;
    private String value;
    private String operand;

    public MetadataValue() {
        setFieldType("value");
    }

    public String getFilterId() {
        return filterId;
    }

    public void setFilterId(String filterId) {
        this.filterId = filterId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getOperand() {
        return operand;
    }

    public void setOperand(String operand) {
        this.operand = operand;
    }
}
