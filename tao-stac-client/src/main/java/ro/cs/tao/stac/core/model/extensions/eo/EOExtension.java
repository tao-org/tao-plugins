package ro.cs.tao.stac.core.model.extensions.eo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.parser.STACParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Electro Optical extension
 *
 * @see <a href="https://github.com/stac-extensions/eo">Electro-Optical Extension Specification</a>
 * @param <E>   The type of the extensible object
 */
public class EOExtension<E extends Extensible> extends Extension<E> {

    public EOExtension(E parent) {
        super(parent);
    }

    @Override
    public String getPrefix() {
        return EOFields.PREFIX;
    }

    @Override
    public void extractField(TreeNode node, String name) throws JsonProcessingException {
        switch (name) {
            case EOFields.BANDS:
                TreeNode arrNode = node.get(name);
                if (arrNode != null) {
                    for (int i = 0; i < arrNode.size(); i++) {
                        addBand(new STACParser().parseBand(arrNode.get(i).toString()));
                    }
                }
                break;
            case EOFields.CLOUD_COVER:
                setCloud_cover(Double.parseDouble(node.get(name).toString()));
                break;
            case EOFields.SNOW_COVER:
                setSnow_cover(Double.parseDouble(node.get(name).toString()));
                break;
        }
    }

    public Double getCloud_cover() {
        return parent.getField(EOFields.CLOUD_COVER);
    }

    public void setCloud_cover(double cloud_cover) {
        parent.addField(EOFields.CLOUD_COVER, cloud_cover);
    }

    public Double getSnow_cover() { return parent.getField(EOFields.SNOW_COVER); }

    public void setSnow_cover(double snow_cover) { parent.addField(EOFields.SNOW_COVER, snow_cover); }

    public List<Band> getBands() {
        return parent.getField(EOFields.BANDS);
    }

    public void setBands(List<Band> bands) {
        parent.addField(EOFields.BANDS, bands);
    }

    public void addBand(Band band) {
        List<Band> bands = parent.getField(EOFields.BANDS);
        if (bands == null) {
            parent.addField(EOFields.BANDS, new ArrayList<>());
            bands = parent.getField(EOFields.BANDS);
        }
        bands.add(band);
    }

}
