package ro.cs.tao.stac.core.model.extensions.projection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.parser.JsonValueHelper;

import java.util.logging.Logger;

/**
 * Projection extension
 *
 * @see <a href="https://github.com/stac-extensions/projection">Projection Extension Specification</a>
 * @param <E>   The type of the extensible object
 */
public class ProjExtension<E extends Extensible> extends Extension<E> {

    public ProjExtension(E parent) {
        super(parent);
    }

    @Override
    public String getPrefix() {
        return ProjFields.PREFIX;
    }

    @Override
    public void extractField(TreeNode node, String name) throws JsonProcessingException {
        try {
            if (ProjFields.CENTROID.equals(name)) {
                TreeNode child = node.get(name);
                Centroid centroid = new Centroid();
                centroid.setLat(JsonValueHelper.getDouble(child, "lat"));
                centroid.setLon(JsonValueHelper.getDouble(child, "lon"));
                setCentroid(centroid);
            } else if (ProjFields.EPSG.equals(name)) {
                setEpsg(JsonValueHelper.getInt(node, name));
            } else if (ProjFields.PROJJSON.equals(name)) {
                setProjjson(JsonValueHelper.getString(node, name));
            } else if (ProjFields.SHAPE.equals(name)) {
                setShape(JsonValueHelper.getDoubleArray1(node, name));
            } else if (ProjFields.TRANSFORM.equals(name)) {
                setTransform(JsonValueHelper.getDoubleArray1(node, name));
            } else if (ProjFields.WKT_2.equals(name)) {
                setWkt2(JsonValueHelper.getString(node, name));
            }
        } catch (Exception e) {
            Logger.getLogger(ProjExtension.class.getName()).warning("Cannot extract field " + name + ": " + e.getMessage());
        }
    }

    public Integer getEpsg() {
        return parent.getField(ProjFields.EPSG);
    }

    public void setEpsg(Integer epsg) {
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
