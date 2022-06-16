package ro.cs.tao.stac.core.model.extensions;

import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.eo.EOExtension;
import ro.cs.tao.stac.core.model.extensions.projection.ProjExtension;
import ro.cs.tao.stac.core.model.extensions.view.ViewExtension;

public class ExtensionFactory {

    public static <E extends Extensible> Extension<E> create(E parent, ExtensionType type) {
        Extension<E> extension = null;
        switch (type) {
            case EO:
                extension = new EOExtension<>(parent);
                break;
            case PROJ:
                extension = new ProjExtension<>(parent);
                break;
            case VIEW:
                extension = new ViewExtension<>(parent);
                break;
        }
        return extension;
    }

}
