package ro.cs.tao.datasource.usgs.json.requests;


import ro.cs.tao.datasource.usgs.json.NameValuePair;

import java.util.List;

public class FieldDescriptor {
    private int fieldId;
    private String name;
    private String fieldLink;
    private List<NameValuePair> valueList;

    public int getFieldId() {
        return fieldId;
    }

    public void setFieldId(int fieldId) {
        this.fieldId = fieldId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFieldLink() {
        return fieldLink;
    }

    public void setFieldLink(String fieldLink) {
        this.fieldLink = fieldLink;
    }

    public List<NameValuePair> getValueList() {
        return valueList;
    }

    public void setValueList(List<NameValuePair> valueList) {
        this.valueList = valueList;
    }
}
