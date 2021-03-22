package ro.cs.tao.datasource.usgs.json.requests;

public class SearchFilter {
    private String filterType;
    private String filterId;

    public String getFilterType() {
        return filterType;
    }

    public void setFilterType(String filterType) {
        this.filterType = filterType;
    }

    public String getFilterId() {
        return filterId;
    }

    public void setFilterId(String filterId) {
        this.filterId = filterId;
    }
}
