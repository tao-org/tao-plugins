package ro.cs.tao.datasource.usgs.json.types;

public class MetadataField {
    private int id;
    private String fieldName;
    private String dictionaryLink;
    private String value;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getDictionaryLink() {
        return dictionaryLink;
    }

    public void setDictionaryLink(String dictionaryLink) {
        this.dictionaryLink = dictionaryLink;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
