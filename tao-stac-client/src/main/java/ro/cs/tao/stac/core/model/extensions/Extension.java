package ro.cs.tao.stac.core.model.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import ro.cs.tao.stac.core.model.Extensible;

public abstract class Extension<E extends Extensible> {
    protected final E parent;

    public Extension(E parent) {
        this.parent = parent;
    }

    public abstract String getPrefix();

    public abstract void extractField(TreeNode node, String name) throws JsonProcessingException;
}
