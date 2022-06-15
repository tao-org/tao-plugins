package ro.cs.tao.stac.core.model.extensions.projection;

import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;

public class ProjExtension<E extends Extensible> extends Extension<E> {

    public ProjExtension(E parent) {
        super(parent);
    }

    @Override
    public boolean isOfThisType(String fieldName) {
        return fieldName.startsWith(ProjFields.PREFIX);
    }

    public Integer getEpsg() {
        return parent.getField(ProjFields.EPSG);
    }

    public void setEpsg(int epsg) {
        parent.addField(ProjFields.EPSG, epsg);
    }

    public String getWkt2() {
        return parent.getField(ProjFields.WKT_2);
    }

    public void setWkt2(String wkt2) {
        parent.addField(ProjFields.WKT_2, wkt2);
    }

    public String getProjjson() {
        return parent.getField(ProjFields.PROJJSON);
    }

    public void setProjjson(String projjson) {
        parent.addField(ProjFields.PROJJSON, projjson);
    }

    public Centroid getCentroid() {
        return parent.getField(ProjFields.CENTROID);
    }

    public void setCentroid(Centroid centroid) {
        parent.addField(ProjFields.CENTROID, centroid);
    }

    public double[] getShape() {
        return parent.getField(ProjFields.SHAPE);
    }

    public void setShape(double[] shape) {
        if (shape != null && shape.length != 2) {
            throw new IllegalArgumentException("Shape must have exact 2 values");
        }
        parent.addField(ProjFields.SHAPE, shape);
    }

    public double[] getTransform() {
        return parent.getField(ProjFields.TRANSFORM);
    }

    public void setTransform(double[] transform) {
        if (transform != null && transform.length != 6 && transform.length != 9) {
            throw new IllegalArgumentException("Transform must have either 6 or 9 values");
        }
        parent.addField(ProjFields.TRANSFORM, transform);
    }
}
