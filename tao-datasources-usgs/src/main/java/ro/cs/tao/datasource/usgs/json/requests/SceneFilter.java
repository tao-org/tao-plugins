package ro.cs.tao.datasource.usgs.json.requests;

import ro.cs.tao.datasource.usgs.json.types.AcquisitionFilter;

public class SceneFilter {
    private SpatialFilter spatialFilter;
    private AcquisitionFilter acquisitionFilter;
    private CloudCoverFilter cloudCoverFilter;
    private TemporalFilter ingestFilter;
    private SearchFilter metadataFilter;
    private int[] seasonalFilter;

    public SpatialFilter getSpatialFilter() {
        return spatialFilter;
    }

    public void setSpatialFilter(SpatialFilter spatialFilter) {
        this.spatialFilter = spatialFilter;
    }

    public AcquisitionFilter getAcquisitionFilter() {
        return acquisitionFilter;
    }

    public void setAcquisitionFilter(AcquisitionFilter acquisitionFilter) {
        this.acquisitionFilter = acquisitionFilter;
    }

    public CloudCoverFilter getCloudCoverFilter() {
        return cloudCoverFilter;
    }

    public void setCloudCoverFilter(CloudCoverFilter cloudCoverFilter) {
        this.cloudCoverFilter = cloudCoverFilter;
    }

    public TemporalFilter getIngestFilter() {
        return ingestFilter;
    }

    public void setIngestFilter(TemporalFilter ingestFilter) {
        this.ingestFilter = ingestFilter;
    }

    public int[] getSeasonalFilter() {
        return seasonalFilter;
    }

    public void setSeasonalFilter(int[] seasonalFilter) {
        this.seasonalFilter = seasonalFilter;
    }

    public SearchFilter getMetadataFilter() {
        return metadataFilter;
    }

    public void setMetadataFilter(SearchFilter metadataFilter) {
        this.metadataFilter = metadataFilter;
    }

    private void addFilter(SearchFilter filter) {
        if (this.metadataFilter == null) {
            this.metadataFilter = new SearchFilterAnd();
        }
        final SearchFilterAnd metadataFilter = (SearchFilterAnd) this.metadataFilter;
        metadataFilter.addChildFilter(filter);
    }
}
