package ro.cs.tao.datasource.usgs.json.responses;

import ro.cs.tao.datasource.usgs.json.types.*;

import java.util.List;

public class SearchResult {
    private List<Browse> browse;
    private String cloudCover;
    private String entityId;
    private String displayId;
    private String orderingId;
    private List<MetadataField> metadata;
    private Options options;
    private Selected selected;
    private SpatialFootprint spatialCoverage;
    private SpatialFootprint spatialBounds;
    private DateRange temporalCoverage;
    private String publishDate;

    public List<Browse> getBrowse() {
        return browse;
    }

    public void setBrowse(List<Browse> browse) {
        this.browse = browse;
    }

    public String getCloudCover() {
        return cloudCover;
    }

    public void setCloudCover(String cloudCover) {
        this.cloudCover = cloudCover;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDisplayId() {
        return displayId;
    }

    public void setDisplayId(String displayId) {
        this.displayId = displayId;
    }

    public List<MetadataField> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<MetadataField> metadata) {
        this.metadata = metadata;
    }

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    public Selected getSelected() {
        return selected;
    }

    public void setSelected(Selected selected) {
        this.selected = selected;
    }

    public SpatialFootprint getSpatialCoverage() {
        return spatialCoverage;
    }

    public void setSpatialCoverage(SpatialFootprint spatialCoverage) {
        this.spatialCoverage = spatialCoverage;
    }

    public SpatialFootprint getSpatialBounds() {
        return spatialBounds;
    }

    public void setSpatialBounds(SpatialFootprint spatialBounds) {
        this.spatialBounds = spatialBounds;
    }

    public DateRange getTemporalCoverage() {
        return temporalCoverage;
    }

    public void setTemporalCoverage(DateRange temporalCoverage) {
        this.temporalCoverage = temporalCoverage;
    }

    public String getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(String publishDate) {
        this.publishDate = publishDate;
    }

    public String getOrderingId() {
        return orderingId;
    }

    public void setOrderingId(String orderingId) {
        this.orderingId = orderingId;
    }
}
