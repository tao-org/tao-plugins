package ro.cs.tao.datasource.usgs.json.types;

public class SpatialFilterGeoJson extends SpatialFilter {
    private GeoJson geoJson;

    public SpatialFilterGeoJson() {
        setFilterType("geoJson");
    }

    public GeoJson getGeoJson() {
        return geoJson;
    }

    public void setGeoJson(GeoJson geoJson) {
        this.geoJson = geoJson;
    }
}
