package ro.cs.tao.stac.core.model.extensions;

import ro.cs.tao.TaoEnum;
import ro.cs.tao.stac.core.model.extensions.eo.EOExtension;
import ro.cs.tao.stac.core.model.extensions.eo.EoFields;
import ro.cs.tao.stac.core.model.extensions.fileinfo.FileInfoExtension;
import ro.cs.tao.stac.core.model.extensions.fileinfo.FileInfoFields;
import ro.cs.tao.stac.core.model.extensions.projection.ProjExtension;
import ro.cs.tao.stac.core.model.extensions.projection.ProjFields;
import ro.cs.tao.stac.core.model.extensions.sentinel2.S2Fields;
import ro.cs.tao.stac.core.model.extensions.sentinel2.Sentinel2Extension;
import ro.cs.tao.stac.core.model.extensions.view.ViewExtension;
import ro.cs.tao.stac.core.model.extensions.view.ViewFields;

/**
 * Enumeration of the supported extension types.
 *
 * @author Cosmin Cara
 */
public enum ExtensionType implements TaoEnum<String> {
    EO(EOExtension.class, EoFields.PREFIX,
       "https://stac-extensions.github.io/eo/v1.0.0/schema.json"),
    PROJ(ProjExtension.class,
         ProjFields.PREFIX,
         "https://stac-extensions.github.io/projection/v1.0.0/schema.json"),
    VIEW(ViewExtension.class,
         ViewFields.PREFIX,
         "https://stac-extensions.github.io/view/v1.0.0/schema.json"),
    FILE(FileInfoExtension.class,
         FileInfoFields.PREFIX,
         "https://stac-extensions.github.io/file/v1.0.0/schema.json"),
    S2(Sentinel2Extension.class,
       S2Fields.PREFIX,
       "https://stac-extensions.github.io/sentinel-2/v1.0.0/schema.json");

    private final String value;
    private final String description;
    private final Class<? extends Extension> extClass;

    ExtensionType(Class<? extends Extension> clazz, String value, String description) {
        this.value = value;
        this.description = description;
        this.extClass = clazz;
    }

    @Override
    public String friendlyName() { return this.description; }

    @Override
    public String value() { return this.value; }

    public static ExtensionType fromURI(String uri) {
        for (ExtensionType type : ExtensionType.values()) {
            if (type.description.equals(uri)) {
                return type;
            }
        }
        return null;
    }
}
