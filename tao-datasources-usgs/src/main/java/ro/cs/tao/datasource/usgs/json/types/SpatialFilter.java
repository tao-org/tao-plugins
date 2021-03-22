package ro.cs.tao.datasource.usgs.json.types;

public abstract class SpatialFilter {
    private String filterType;

    public String getFilterType() {
        return filterType;
    }

    public void setFilterType(String filterType) {
        this.filterType = filterType;
    }
}
