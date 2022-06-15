package ro.cs.tao.stac.core.model.extensions.view;

import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;

public class ViewExtension<E extends Extensible> extends Extension<E> {

    public ViewExtension(E parent) {
        super(parent);
    }

    @Override
    public boolean isOfThisType(String fieldName) {
        return fieldName.startsWith(ViewFields.PREFIX);
    }

    public double getOff_nadir() {
        return parent.getField(ViewFields.OFF_NADIR);
    }

    public void setOff_nadir(double value) {
        if (value < 0 || value > 90) {
            throw new IllegalArgumentException("Illegal off_nadir value");
        }
        parent.addField(ViewFields.OFF_NADIR, value);
    }

    public double getIncidence_angle() {
        return parent.getField(ViewFields.INCIDENCE_ANGLE);
    }

    public void setIncidence_angle(double value) {
        if (value < 0 || value > 90) {
            throw new IllegalArgumentException("Illegal incidence_angle value");
        }
        parent.addField(ViewFields.INCIDENCE_ANGLE, value);
    }

    public double getAzimuth() {
        return parent.getField(ViewFields.AZIMUTH);
    }

    public void setAzimuth(double value) {
        if (value < 0 || value > 360) {
            throw new IllegalArgumentException("Illegal azimuth value");
        }
        parent.addField(ViewFields.AZIMUTH, value);
    }

    public double getSun_azimuth() {
        return parent.getField(ViewFields.SUN_AZIMUTH);
    }

    public void setSun_azimuth(double value) {
        if (value < 0 || value > 360) {
            throw new IllegalArgumentException("Illegal sun_azimuth value");
        }
        parent.addField(ViewFields.SUN_AZIMUTH, value);
    }

    public double getSun_elevation() {
        return parent.getField(ViewFields.SUN_ELEVATION);
    }

    public void setSun_elevation(double value) {
        if (value < -90 || value > 90) {
            throw new IllegalArgumentException("Illegal sun_elevation value");
        }
        parent.addField(ViewFields.SUN_ELEVATION, value);
    }
}
