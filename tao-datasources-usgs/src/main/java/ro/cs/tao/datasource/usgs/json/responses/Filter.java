package ro.cs.tao.datasource.usgs.json.responses;

public class Filter {

    protected String type;
    protected String[] options;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String[] getOptions() {
        return options;
    }

    public void setOptions(String[] options) {
        this.options = options;
    }
}
