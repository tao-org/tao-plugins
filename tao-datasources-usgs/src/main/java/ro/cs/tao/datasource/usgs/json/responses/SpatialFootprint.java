package ro.cs.tao.datasource.usgs.json.responses;

import java.util.List;

public class SpatialFootprint {
    private String type;
    private Number[][][] coordinates;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Number[][][] getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Object coordinates) {
        switch (type) {
            case "LineString":
                this.coordinates = new Number[][][]{((List<?>) coordinates).stream().map(u -> ((List<?>) u).stream().map(n -> (Number) n).toArray(Number[]::new)).toArray(Number[][]::new)};
                break;
            case "Polygon":
                this.coordinates = ((List<?>) coordinates).stream().map(u -> ((List<?>) u).stream().map(v -> ((List<?>) v).stream().map(n -> (Number) n).toArray(Number[]::new)).toArray(Number[][]::new)).toArray(Number[][][]::new);
                break;
            default:
                this.coordinates = new Number[][][]{};
        }
    }
}
