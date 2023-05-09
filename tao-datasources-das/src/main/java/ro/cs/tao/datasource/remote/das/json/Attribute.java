package ro.cs.tao.datasource.remote.das.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Attribute{
    @JsonProperty("@odata.type")
    private String oDataType;
    @JsonProperty("Name")
    private String name;
    @JsonProperty("Value")
    private Object value;
    @JsonProperty("ValueType")
    private String valueType;

    @JsonProperty("@odata.type")
    public String getoDataType() {
        return oDataType;
    }
    @JsonProperty("@odata.type")
    public void setoDataType(String oDataType) {
        this.oDataType = oDataType;
    }
    @JsonProperty("Name")
    public String getName() {
        return name;
    }
    @JsonProperty("Name")
    public void setName(String name) {
        this.name = name;
    }
    @JsonProperty("Value")
    public Object getValue() {
        return value;
    }
    @JsonProperty("Value")
    public void setValue(Object value) {
        this.value = value;
    }
    @JsonProperty("ValueType")
    public String getValueType() {
        return valueType;
    }
    @JsonProperty("ValueType")
    public void setValueType(String valueType) {
        this.valueType = valueType;
    }
}
