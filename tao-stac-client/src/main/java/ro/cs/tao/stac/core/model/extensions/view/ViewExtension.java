package ro.cs.tao.stac.core.model.extensions.view;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.parser.JsonValueHelper;

/**
 * View Geometry extension
 *
 * @see <a href="https://github.com/stac-extensions/view">View Geometry Extension Specification</a>
 * @param <E>   The type of the extensible object
 */
public class ViewExtension<E extends Extensible> extends Extension<E> {

    public ViewExtension(E parent) {
        super(parent);
    }

    @Override
    public String getPrefix() {
        return ViewFields.PREFIX;
    }

    @Override
    public void extractField(TreeNode node, String name) throws JsonProcessingException {
        switch (name) {
            case ViewFields.AZIMUTH:
                setAzimuth(JsonValueHelper.getDouble(node, name));
                break;
            case ViewFields.INCIDENCE_ANGLE:
                setIncidence_angle(JsonValueHelper.getDouble(node, name));
                break;
            case ViewFields.OFF_NADIR:
                setOff_nadir(JsonValueHelper.getDouble(node, name));
                break;
            case ViewFields.SUN_AZIMUTH:
                setSun_azimuth(JsonValueHelper.getDouble(node, name));
                break;
            case ViewFields.SUN_ELEVATION:
                setSun_elevation(JsonValueHelper.getDouble(node, name));
                break;
        }
    }

    public Double getOff_nadir() {
        return parent.getField(ViewFields.OFF_NADIR);
    }

    public void setOff_nadir(Double value) {
        if (value != null && (value < 0 || value > 90)) {
            throw new IllegalArgumentException("Illegal off_nadir value");
        }
        parent.addField(ViewFields.OFF_NADIR, value);
    }

    public Double getIncidence_angle() {
        return parent.getField(ViewFields.INCIDENCE_ANGLE);
    }

    public void setIncidence_angle(Double value) {
        if (value != null && (value < 0 || value > 90)) {
            throw new IllegalArgumentException("Illegal incidence_angle value");
        }
        parent.addField(ViewFields.INCIDENCE_ANGLE, value);
    }

    public Double getAzimuth() {
        return parent.getField(ViewFields.AZIMUTH);
    }

    public void setAzimuth(Double value) {
        if (value != null && (value < 0 || value > 360)) {
            throw new IllegalArgumentException("Illegal azimuth value");
        }
        parent.addField(ViewFields.AZIMUTH, value);
    }

    public Double getSun_azimuth() {
        return parent.getField(ViewFields.SUN_AZIMUTH);
    }

    public void setSun_azimuth(Double value) {
        if (value != null && (value < 0 || value > 360)) {
            throw new IllegalArgumentException("Illegal sun_azimuth value");
        }
        parent.addField(ViewFields.SUN_AZIMUTH, value);
    }

    public Double getSun_elevation() {
        return parent.getField(ViewFields.SUN_ELEVATION);
    }

    public void setSun_elevation(Double value) {
        if (value != null && (value < -90 || value > 90)) {
            throw new IllegalArgumentException("Illegal sun_elevation value");
        }
        parent.addField(ViewFields.SUN_ELEVATION, value);
    }
}
