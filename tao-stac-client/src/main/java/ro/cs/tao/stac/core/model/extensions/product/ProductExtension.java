package ro.cs.tao.stac.core.model.extensions.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.parser.JsonValueHelper;

import java.util.logging.Logger;

public class ProductExtension<E extends Extensible> extends Extension<E> {

    public ProductExtension(E parent) {
        super(parent);
    }

    @Override
    public String getPrefix() {
        return ProductFields.PREFIX;
    }

    @Override
    public void extractField(TreeNode node, String name) throws JsonProcessingException {
        try {
            switch (name) {
                case ProductFields.TYPE:
                    setType(JsonValueHelper.getString(node, name));
                    break;
                case ProductFields.TIMELINESS:
                    setTimeliness(JsonValueHelper.getString(node, name));
                    break;
                case ProductFields.TIMELINESS_CATEGORY:
                    setTimelinessCategory(JsonValueHelper.getString(node, name));
                    break;
            }
        } catch (Exception e) {
            Logger.getLogger(ProductExtension.class.getName()).warning("Cannot extract field " + name + ": " + e.getMessage());
        }
    }

    public String getType() {
        return parent.getField(ProductFields.TYPE);
    }

    public void setType(String value) {
        parent.addField(ProductFields.TYPE, value);
    }

    public String getTimeliness() {
        return parent.getField(ProductFields.TIMELINESS);
    }

    public void setTimeliness(String value) {
        parent.addField(ProductFields.TIMELINESS, value);
    }

    public String getTimelinessCategory() {
        return parent.getField(ProductFields.TIMELINESS_CATEGORY);
    }

    public void setTimelinessCategory(String value) {
        parent.addField(ProductFields.TIMELINESS_CATEGORY, value);
    }
}
