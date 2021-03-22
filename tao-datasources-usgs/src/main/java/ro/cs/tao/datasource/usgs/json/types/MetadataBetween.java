package ro.cs.tao.datasource.usgs.json.types;

public class MetadataBetween extends MetadataFilter {
    private String filterId;
    private int firstValue;
    private int secondValue;

    public MetadataBetween() {
        setFieldType("between");
    }

    public String getFilterId() {
        return filterId;
    }

    public void setFilterId(String filterId) {
        this.filterId = filterId;
    }

    public int getFirstValue() {
        return firstValue;
    }

    public void setFirstValue(int firstValue) {
        this.firstValue = firstValue;
    }

    public int getSecondValue() {
        return secondValue;
    }

    public void setSecondValue(int secondValue) {
        this.secondValue = secondValue;
    }
}
