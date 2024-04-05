package ro.cs.tao.datasource.usgs.json.requests;

import ro.cs.tao.datasource.usgs.json.Point;
import ro.cs.tao.datasource.usgs.json.types.AcquisitionFilter;

public class SearchRequest extends BaseRequest {
    private String datasetName;
    private SceneFilter sceneFilter;
    private int maxResults = 10;
    private int startingNumber = 1;
    private String sortDirection = "ASC";
    private String compareListName;

    public String getDatasetName() {
        return datasetName;
    }

    private void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public SceneFilter getSceneFilter() { return sceneFilter; }
    private void setSceneFilter(SceneFilter sceneFilter) { this.sceneFilter = sceneFilter; }

    /*public SearchFilter getAdditionalCriteria() { return additionalCriteria; }
    private void setAdditionalCriteria(SearchFilter additionalCriteria) { this.additionalCriteria = additionalCriteria; }*/

    public int getMaxResults() { return maxResults; }
    private void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public int getStartingNumber() { return startingNumber; }
    private void setStartingNumber(int startingNumber) { this.startingNumber = startingNumber; }

    public String getSortDirection() { return sortDirection; }
    private void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }

    public String getCompareListName() {
        return compareListName;
    }

    public void setCompareListName(String compareListName) {
        this.compareListName = compareListName;
    }

    private void addFilter(SearchFilter filter) {
        if (this.sceneFilter == null) {
            this.sceneFilter = new SceneFilter();
        }
        SearchFilterAnd metadataFilter = (SearchFilterAnd) this.sceneFilter.getMetadataFilter();
        if (metadataFilter == null) {
            metadataFilter = new SearchFilterAnd();
            this.sceneFilter.setMetadataFilter(metadataFilter);
        }
        metadataFilter.addChildFilter(filter);
    }

    public SearchRequest withDataSet(String dataset) {
        setDatasetName(dataset);
        return this;
    }

    public SearchRequest withLowerLeft(double longitude, double latitude) {
        if (this.sceneFilter == null) {
            this.sceneFilter = new SceneFilter();
        }
        if (this.sceneFilter.getSpatialFilter() == null) {
            this.sceneFilter.setSpatialFilter(new SpatialFilter());
        }
        this.sceneFilter.getSpatialFilter().setLowerLeft(new Point(latitude, longitude));
        return this;
    }

    public SearchRequest withUpperRight(double longitude, double latitude) {
        if (this.sceneFilter == null) {
            this.sceneFilter = new SceneFilter();
        }
        if (this.sceneFilter.getSpatialFilter() == null) {
            this.sceneFilter.setSpatialFilter(new SpatialFilter());
        }
        this.sceneFilter.getSpatialFilter().setUpperRight(new Point(latitude, longitude));
        return this;
    }

    public SearchRequest withStartDate(String date) {
        if (this.sceneFilter == null) {
            this.sceneFilter = new SceneFilter();
        }
        if (this.sceneFilter.getAcquisitionFilter() == null) {
            this.sceneFilter.setAcquisitionFilter(new AcquisitionFilter());
        }
        this.sceneFilter.getAcquisitionFilter().setStart(date);
        return this;
    }

    public SearchRequest withEndDate(String date) {
        if (this.sceneFilter == null) {
            this.sceneFilter = new SceneFilter();
        }
        if (this.sceneFilter.getAcquisitionFilter() == null) {
            this.sceneFilter.setAcquisitionFilter(new AcquisitionFilter());
        }
        this.sceneFilter.getAcquisitionFilter().setEnd(date);
        return this;
    }

    public SearchRequest withMinClouds(int clouds) {
        if (this.sceneFilter == null) {
            this.sceneFilter = new SceneFilter();
        }
        if (this.sceneFilter.getCloudCoverFilter() == null) {
            this.sceneFilter.setCloudCoverFilter(new CloudCoverFilter());
        }
        this.sceneFilter.getCloudCoverFilter().setIncludeUnknown(false);
        this.sceneFilter.getCloudCoverFilter().setMin(clouds);
        return this;
    }

    public SearchRequest withMaxClouds(int clouds) {
        if (this.sceneFilter == null) {
            this.sceneFilter = new SceneFilter();
        }
        if (this.sceneFilter.getCloudCoverFilter() == null) {
            this.sceneFilter.setCloudCoverFilter(new CloudCoverFilter());
        }
        this.sceneFilter.getCloudCoverFilter().setIncludeUnknown(false);
        this.sceneFilter.getCloudCoverFilter().setMax(clouds);
        return this;
    }

    public SearchRequest withCompareListName(String value) {
        setCompareListName(value);
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
        setSortDirection(sort);
        return this;
    }

    public SearchRequest withFilter(SearchFilter filter) {
        addFilter(filter);
        return this;
    }
}
