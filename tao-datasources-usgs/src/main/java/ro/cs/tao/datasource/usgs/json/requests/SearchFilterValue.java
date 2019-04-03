package ro.cs.tao.datasource.usgs.json.requests;

public class SearchFilterValue extends SearchFilter{
    private String value;
    private String operand;

    public SearchFilterValue() {
        setFilterType("value");
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getOperand() { return operand; }
    public void setOperand(String operand) { this.operand = operand; }
}
