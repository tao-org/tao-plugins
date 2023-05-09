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
        return EoFields.PREFIX;
    }

    @Override
    public void extractField(TreeNode node, String name) throws JsonProcessingException {
        if (EoFields.BANDS.equals(name)) {
            TreeNode arrNode = node.get(name);
            if (arrNode != null) {
                for (int i = 0; i < arrNode.size(); i++) {
                    addBand(new STACParser().parseBand(arrNode.get(i).toString()));
                }
            }
        } else if (EoFields.CLOUD_COVER.equals(name)) {
            setCloud_cover(Double.parseDouble(node.get(name).toString()));
        }
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
