package ro.cs.tao.stac.core.model.extensions.projection;

public class Centroid {
    private double lat;
    private double lon;

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Invalid latitude value");
        }
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Invalid longitude value");
        }
        this.lon = lon;
    }
}
