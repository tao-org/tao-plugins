package ro.cs.tao.datasource.usgs.json.requests;

public class SearchFilter {
    private String filterType;
    private int fieldId;

    public String getFilterType() {
        return filterType;
    }

    public void setFilterType(String filterType) {
        this.filterType = filterType;
    }

    public int getFieldId() {
        return fieldId;
    }

    public void setFieldId(int fieldId) {
        this.fieldId = fieldId;
    }
}
