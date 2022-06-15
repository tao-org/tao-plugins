package ro.cs.tao.stac.core.model.extensions.eo;

import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;

import java.util.ArrayList;
import java.util.List;

public class EOExtension<E extends Extensible> extends Extension<E> {

    public EOExtension(E parent) {
        super(parent);
    }

    @Override
    public boolean isOfThisType(String fieldName) {
        return fieldName.startsWith(EoFields.PREFIX);
    }

    public Double getCloud_cover() {
        return parent.getField(EoFields.CLOUD_COVER);
    }

    public void setCloud_cover(double cloud_cover) {
        parent.addField(EoFields.CLOUD_COVER, cloud_cover);
    }

    public List<Band> getBands() {
        return parent.getField(EoFields.BANDS);
    }

    public void setBands(List<Band> bands) {
        parent.addField(EoFields.BANDS, bands);
    }

    public void addBand(Band band) {
        List<Band> bands = parent.getField(EoFields.BANDS);
        if (bands == null) {
            parent.addField(EoFields.BANDS, new ArrayList<>());
            bands = parent.getField(EoFields.BANDS);
        }
        bands.add(band);
    }

}
