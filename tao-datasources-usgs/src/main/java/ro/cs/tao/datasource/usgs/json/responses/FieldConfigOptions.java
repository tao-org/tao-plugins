package ro.cs.tao.datasource.usgs.json.responses;

public class FieldConfigOptions {
    private String size;
    private Boolean multiple;

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public Boolean isMultiple() {
        return multiple;
    }

    public void setMultiple(Boolean multiple) {
        this.multiple = multiple;
    }
}
