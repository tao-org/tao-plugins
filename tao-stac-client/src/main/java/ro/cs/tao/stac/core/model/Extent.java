package ro.cs.tao.stac.core.model;

import javax.annotation.Nonnull;

public class Extent {
    protected SpatialExtent spatial;
    protected TemporalExtent temporal;

    public SpatialExtent getSpatial() {
        return spatial;
    }

    public void setSpatial(@Nonnull SpatialExtent spatial) {
        this.spatial = spatial;
    }

    public TemporalExtent getTemporal() {
        return temporal;
    }

    public void setTemporal(@Nonnull TemporalExtent temporal) {
        this.temporal = temporal;
    }
}
