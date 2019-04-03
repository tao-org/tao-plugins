package ro.cs.tao.datasource.usgs.json.requests;

import java.util.ArrayList;
import java.util.List;

public class SearchFilterOr extends SearchFilter {
    private List<SearchFilter> childFilters;

    public SearchFilterOr() {
        setFilterType("or");
    }

    public List<SearchFilter> getChildFilters() {
        return childFilters;
    }

    public void setChildFilters(List<SearchFilter> childFilters) {
        this.childFilters = childFilters;
    }

    public void addChildFilter(SearchFilter child) {
        if (this.childFilters == null) {
            this.childFilters = new ArrayList<>();
        }
        this.childFilters.add(child);
    }
}
