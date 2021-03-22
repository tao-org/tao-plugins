package ro.cs.tao.datasource.usgs.json.requests;


import ro.cs.tao.datasource.usgs.json.responses.FieldConfig;

import java.util.Map;

/**
 * FIeld descriptor bean for USGS API 1.5
 */
public class FieldDescriptor {
    private String id;
    private String legacyFieldId;
    private String dictionaryLink;
    private FieldConfig fieldConfig;
    private String fieldLabel;
    private String searchSql;
    private Map<String, String> valueList;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLegacyFieldId() {
        return legacyFieldId;
    }

    public void setLegacyFieldId(String legacyFieldId) {
        this.legacyFieldId = legacyFieldId;
    }

    public String getDictionaryLink() {
        return dictionaryLink;
    }

    public void setDictionaryLink(String dictionaryLink) {
        this.dictionaryLink = dictionaryLink;
    }

    public FieldConfig getFieldConfig() {
        return fieldConfig;
    }

    public void setFieldConfig(FieldConfig fieldConfig) {
        this.fieldConfig = fieldConfig;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }

    public String getSearchSql() {
        return searchSql;
    }

    public void setSearchSql(String searchSql) {
        this.searchSql = searchSql;
    }

    public Map<String, String> getValueList() {
        return valueList;
    }

    public void setValueList(Map<String, String> valueList) {
        this.valueList = valueList;
    }
}
