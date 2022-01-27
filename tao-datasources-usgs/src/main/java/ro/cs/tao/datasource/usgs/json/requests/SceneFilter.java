package ro.cs.tao.datasource.usgs.json.requests;

import ro.cs.tao.datasource.usgs.json.types.AcquisitionFilter;

public class SceneFilter {
    private SpatialFilter spatialFilter;
    private AcquisitionFilter acquisitionFilter;
    private CloudCoverFilter cloudCoverFilter;
    private TemporalFilter ingestFilter;
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
}
