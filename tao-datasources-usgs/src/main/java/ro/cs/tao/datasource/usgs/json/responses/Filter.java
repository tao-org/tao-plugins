package ro.cs.tao.datasource.usgs.json.responses;

public class Filter {

    protected String type;
    protected Object options;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getOptions() {
        return options;
    }

    public void setOptions(Object options) {
        this.options = options;
    }
}
