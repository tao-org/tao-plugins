package ro.cs.tao.datasource.usgs.json.types;

import java.util.ArrayList;
import java.util.List;

public class GeoJson extends SpatialBounds {
    private String type;
    private List<Coordinate> coordinates;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Coordinate> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<Coordinate> coordinates) {
        this.coordinates = coordinates;
    }

    public void addCoordinate(Coordinate coordinate) {
        if (this.coordinates == null) {
            this.coordinates = new ArrayList<>();
        }
        this.coordinates.add(coordinate);
    }

    public void addCoordinate(double longitude, double latitude) {
        addCoordinate(new Coordinate() {{ setLongitude(longitude); setLatitude(latitude); }});
    }
}
