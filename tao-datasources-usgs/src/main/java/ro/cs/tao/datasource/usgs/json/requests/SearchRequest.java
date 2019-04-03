package ro.cs.tao.datasource.usgs.json.requests;

import ro.cs.tao.datasource.usgs.json.Point;

public class SearchRequest extends BaseRequest {
    private SpatialFilter spatialFilter;
    private TemporalFilter temporalFilter;
    private int minCloudCover = 0;
    private int maxCloudCover = 100;
    private SearchFilter additionalCriteria;
    private int maxResults = 10;
    private int startingNumber = 1;
    private String sortOrder = "ASC";

    public SpatialFilter getSpatialFilter() { return spatialFilter; }
    private void setSpatialFilter(SpatialFilter spatialFilter) { this.spatialFilter = spatialFilter; }

    public TemporalFilter getTemporalFilter() { return temporalFilter; }
    private void setTemporalFilter(TemporalFilter temporalFilter) { this.temporalFilter = temporalFilter; }

    public int getMinCloudCover() { return minCloudCover; }
    private void setMinCloudCover(int minCloudCover) { this.minCloudCover = minCloudCover; }

    public int getMaxCloudCover() { return maxCloudCover; }
    private void setMaxCloudCover(int maxCloudCover) { this.maxCloudCover = maxCloudCover; }

    public SearchFilter getAdditionalCriteria() { return additionalCriteria; }
    private void setAdditionalCriteria(SearchFilter additionalCriteria) { this.additionalCriteria = additionalCriteria; }

    public int getMaxResults() { return maxResults; }
    private void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public int getStartingNumber() { return startingNumber; }
    private void setStartingNumber(int startingNumber) { this.startingNumber = startingNumber; }

    public String getSortOrder() { return sortOrder; }
    private void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }

    private void addFilter(SearchFilter filter) {
        if (this.additionalCriteria == null) {
            this.additionalCriteria = filter;
        } else {
            if (this.additionalCriteria instanceof SearchFilterAnd) {
                ((SearchFilterAnd) this.additionalCriteria).addChildFilter(filter);
            } else {
                SearchFilter existing = this.additionalCriteria;
                this.additionalCriteria = new SearchFilterAnd();
                ((SearchFilterAnd) this.additionalCriteria).addChildFilter(existing);
                ((SearchFilterAnd) this.additionalCriteria).addChildFilter(filter);
            }
        }
    }

    public SearchRequest withAPIKey(String apiKey) {
        setApiKey(apiKey);
        return this;
    }

    public SearchRequest withDataSet(String dataset) {
        setDatasetName(dataset);
        return this;
    }

    public SearchRequest withLowerLeft(double longitude, double latitude) {
        if (this.spatialFilter == null) {
            this.spatialFilter = new SpatialFilter();
        }
        this.spatialFilter.setLowerLeft(new Point(latitude, longitude));
        return this;
    }

    public SearchRequest withUpperRight(double longitude, double latitude) {
        if (this.spatialFilter == null) {
            this.spatialFilter = new SpatialFilter();
        }
        this.spatialFilter.setUpperRight(new Point(latitude, longitude));
        return this;
    }

    public SearchRequest withStartDate(String date) {
        if (this.temporalFilter == null) {
            this.temporalFilter = new TemporalFilter();
        }
        this.temporalFilter.setStartDate(date);
        return this;
    }

    public SearchRequest withEndDate(String date) {
        if (this.temporalFilter == null) {
            this.temporalFilter = new TemporalFilter();
        }
        this.temporalFilter.setEndDate(date);
        return this;
    }

    public SearchRequest withMinClouds(int clouds) {
        setMinCloudCover(clouds);
        return this;
    }

    public SearchRequest withMaxClouds(int clouds) {
        setMaxCloudCover(clouds);
        return this;
    }

    public SearchRequest withMaxResults(int limit) {
        setMaxResults(limit);
        return this;
    }

    public SearchRequest startingAtIndex(int first) {
        setStartingNumber(first);
        return this;
    }

    public SearchRequest sortDirection(String sort) {
        setSortOrder(sort);
        return this;
    }

    public SearchRequest withFilter(SearchFilter filter) {
        addFilter(filter);
        return this;
    }
}
