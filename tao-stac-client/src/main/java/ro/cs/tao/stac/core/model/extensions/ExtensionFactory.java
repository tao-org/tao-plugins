package ro.cs.tao.stac.core.model.extensions;

import ro.cs.tao.stac.core.model.Extensible;
import ro.cs.tao.stac.core.model.extensions.eo.EOExtension;
import ro.cs.tao.stac.core.model.extensions.fileinfo.FileInfoExtension;
import ro.cs.tao.stac.core.model.extensions.product.ProductExtension;
import ro.cs.tao.stac.core.model.extensions.projection.ProjExtension;
import ro.cs.tao.stac.core.model.extensions.raster.RasterExtension;
import ro.cs.tao.stac.core.model.extensions.sentinel2.Sentinel2Extension;
import ro.cs.tao.stac.core.model.extensions.view.ViewExtension;

/**
 * Factory class for creating extensions of a given type.
 *
 * @author Cosmin Cara
 */
public class ExtensionFactory {

    /**
     * Creates an extension for the given parent object
     * @param parent    The parent object
     * @param type      The type of extension
     * @param <E>       The Java type of the parent
     */
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
            case FILE:
                extension = new FileInfoExtension<>(parent);
                break;
            case S2:
                extension = new Sentinel2Extension<>(parent);
                break;
            case PRODUCT:
                extension = new ProductExtension<>(parent);
                break;
            case RASTER:
                extension = new RasterExtension<>(parent);
        }
        return extension;
    }

}
