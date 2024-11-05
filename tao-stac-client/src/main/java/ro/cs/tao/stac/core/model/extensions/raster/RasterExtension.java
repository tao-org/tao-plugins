package ro.cs.tao.stac.core.model.extensions.raster;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.parser.STACParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RasterExtension<E extends Extensible> extends Extension<E> {

    public RasterExtension(E parent) {
        super(parent);
    }

    @Override
    public String getPrefix() {
        return RasterFields.PREFIX;
    }

    @Override
    public void extractField(TreeNode node, String name) throws JsonProcessingException {
        if (RasterFields.BANDS.equals(name)) {
            TreeNode arrNode = node.get(name);
            if (arrNode != null) {
                for (int i = 0; i < arrNode.size(); i++) {
                    try {
                        addBand(new STACParser().parse(arrNode.get(i).toString(), Band.class));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public void addBand(Band band) {
        List<Band> bands = parent.getField(RasterFields.BANDS);
        if (bands == null) {
            parent.addField(RasterFields.BANDS, new ArrayList<>());
            bands = parent.getField(RasterFields.BANDS);
        }
        bands.add(band);
    }
}
