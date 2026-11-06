package ro.cs.tao.stac.core.model.extensions;

import ro.cs.tao.stac.core.model.Extensible;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

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
     * @throws JacksonException
     */
    public abstract void extractField(JsonNode node, String name) throws JacksonException;
}


