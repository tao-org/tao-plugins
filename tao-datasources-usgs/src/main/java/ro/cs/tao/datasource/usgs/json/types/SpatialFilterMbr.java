package ro.cs.tao.datasource.usgs.json.types;

public class SpatialFilterMbr extends SpatialFilter {
    private Coordinate lowerLeft;
    private Coordinate upperRight;

    public SpatialFilterMbr() {
        setFilterType("mbr");
    }

    public Coordinate getLowerLeft() {
        return lowerLeft;
    }

    public void setLowerLeft(Coordinate lowerLeft) {
        this.lowerLeft = lowerLeft;
    }

    public Coordinate getUpperRight() {
        return upperRight;
    }

    public void setUpperRight(Coordinate upperRight) {
        this.upperRight = upperRight;
    }
}
