package ro.cs.tao.stac.core.model.extensions.projection;

public class Centroid {
    private Double lat;
    private Double lon;

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        if (lat != null && (lat < -90 || lat > 90)) {
            throw new IllegalArgumentException("Invalid latitude value");
        }
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        if (lon != null && (lon < -180 || lon > 180)) {
            throw new IllegalArgumentException("Invalid longitude value");
        }
        this.lon = lon;
    }
}
