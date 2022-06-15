package ro.cs.tao.stac.core.model.extensions;

import ro.cs.tao.stac.core.model.Asset;

public abstract class AssetExtension {
    protected final Asset parent;

    public AssetExtension(Asset parent) {
        this.parent = parent;
    }
}
