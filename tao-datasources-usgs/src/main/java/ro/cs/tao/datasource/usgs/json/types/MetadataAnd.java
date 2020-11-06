package ro.cs.tao.datasource.usgs.json.types;

import java.util.ArrayList;
import java.util.List;

public class MetadataAnd extends MetadataFilter {
    private List<MetadataFilter> childFilters;

    public MetadataAnd() {
        setFieldType("and");
    }

    public List<MetadataFilter> getChildFilters() {
        return childFilters;
    }

    public void setChildFilters(List<MetadataFilter> childFilters) {
        this.childFilters = childFilters;
    }

    public void addChildFilter(MetadataFilter filter) {
        if (this.childFilters == null) {
            this.childFilters = new ArrayList<>();
        }
        this.childFilters.add(filter);
    }
}
