package ro.cs.tao.stac.core.model.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import ro.cs.tao.stac.core.model.Extensible;

/**
 * Base class for an object extension.
 * @param <E>   The type of the parent (object onto which the extension is registered)
 *
 * @author Cosmin Cara
 */
public abstract class Extension<E extends Extensible> {
    protected final E parent;

    public Extension(E parent) {
        this.parent = parent;
    }

    /**
     * Returns the prefix of the extension
     */
    public abstract String getPrefix();
    /**
     * Deserializes the field value from the JSON node.
     * @param node  The JSON node
     * @param name  The name of the field
     * @throws JsonProcessingException
     */
    public abstract void extractField(TreeNode node, String name) throws JsonProcessingException;
}
