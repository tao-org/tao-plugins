package ro.cs.tao.datasource.usgs.json.requests;

import java.util.ArrayList;
import java.util.List;

public class SearchFilterAnd extends SearchFilter {
    private List<SearchFilter> childFilters;

    public SearchFilterAnd() {
        setFilterType("and");
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
