package ro.cs.tao.stac.core.model.extensions;

import ro.cs.tao.stac.core.model.Extensible;

public abstract class Extension<E extends Extensible> {
    protected final E parent;

    public Extension(E parent) {
        this.parent = parent;
    }

    public abstract boolean isOfThisType(String fieldName);
}
