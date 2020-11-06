package ro.cs.tao.datasource.usgs.json.responses;

import java.util.List;

public class FieldConfig {

    private String type;
    private List<Filter> filters;
    private FieldConfigOptions options;
    private List<String> validators;
    private String numElements;
    private String displayListId;

    public List<Filter> getFilters() {
        return filters;
    }

    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }

    public List<String> getValidators() {
        return validators;
    }

    public void setValidators(List<String> validators) {
        this.validators = validators;
    }

    public String getNumElements() {
        return numElements;
    }

    public void setNumElements(String numElements) {
        this.numElements = numElements;
    }

    public String getDisplayListId() {
        return displayListId;
    }

    public void setDisplayListId(String displayListId) {
        this.displayListId = displayListId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FieldConfigOptions getOptions() {
        return options;
    }

    public void setOptions(FieldConfigOptions options) {
        this.options = options;
    }
}
