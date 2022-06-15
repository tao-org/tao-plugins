package ro.cs.tao.stac.core.model;

public class SpatialExtent {
    protected double[] bbox;
    protected String crs;

    public double[] getBbox() {
        return bbox;
    }

    public void setBbox(double[] bbox) {
        this.bbox = bbox;
    }

    public String getCrs() {
        return crs;
    }

    public void setCrs(String crs) {
        this.crs = crs;
    }
}
