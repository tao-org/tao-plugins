package ro.cs.tao.stac.core.model;

import java.util.List;

public abstract class Geometry<T> {
    protected final GeometryType type;
    protected T coordinates;
    protected List<Geometry<T>> geometries;

    public static Geometry<?> create(GeometryType type) {
        Geometry<?> result;
        switch (type) {
            case Point:
                result = new Point();
                break;
            case MultiPoint:
                result = new MultiPoint();
                break;
            case LineString:
                result = new LineString();
                break;
            case MultiLineString:
                result = new MultiLineString();
                break;
            case MultiPolygon:
                result = new MultiPolygon();
                break;
            case Polygon:
            default:
                result = new Polygon();
                break;
        }
        return result;
    }

    protected Geometry(GeometryType type) {
        this.type = type;
    }

    public GeometryType getType() {
        return type;
    }

    public abstract int getRank();

    public T getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(T coordinates) {
        if (this.type != null && this.type == GeometryType.GeometryCollection) {
            throw new IllegalArgumentException("Coordinates not allowed on a geometry collection");
        }
        this.coordinates = coordinates;
    }

    public List<Geometry<T>> getGeometries() {
        return geometries;
    }

    public void setGeometries(List<Geometry<T>> geometries) {
        if (this.type != null && this.type != GeometryType.GeometryCollection) {
            throw new IllegalArgumentException("Geometries not allowed on a simple geometry");
        }
        this.geometries = geometries;
    }

    public static class Point extends Geometry<double[]> {
        public Point() {
            super(GeometryType.Point);
        }

        @Override
        public int getRank() {
            return 1;
        }
    }

    public static class MultiPoint extends Geometry<double[][]>{
        public MultiPoint() {
            super(GeometryType.MultiPoint);
        }

        @Override
        public int getRank() {
            return 2;
        }
    }

    public static class LineString extends Geometry<double[][]>{
        public LineString() {
            super(GeometryType.LineString);
        }

        @Override
        public int getRank() {
            return 2;
        }
    }

    public static class MultiLineString extends Geometry<double[][][]>{
        public MultiLineString() {
            super(GeometryType.MultiLineString);
        }

        @Override
        public int getRank() {
            return 3;
        }
    }

    public static class Polygon extends Geometry<double[][][]>{
        public Polygon() {
            super(GeometryType.Polygon);
        }

        @Override
        public int getRank() {
            return 3;
        }
    }

    public static class MultiPolygon extends Geometry<double[][][][]>{
        public MultiPolygon() {
            super(GeometryType.MultiPolygon);
        }

        @Override
        public int getRank() {
            return 4;
        }
    }
}
