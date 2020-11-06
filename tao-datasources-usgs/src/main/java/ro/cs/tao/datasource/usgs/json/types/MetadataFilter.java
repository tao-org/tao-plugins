package ro.cs.tao.datasource.usgs.json.types;

public abstract class MetadataFilter {
    private String fieldType;

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }
}
