package ro.cs.tao.stac.core.model;

import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.model.extensions.ExtensionFactory;
import ro.cs.tao.stac.core.model.extensions.ExtensionType;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for extensible objects. It defines methods for getting/setting fields of the extensible object
 * and for registering extensions (extending the object).
 *
 * @author Cosmin Cara
 */
public class Extensible {
    protected Map<ExtensionType, Extension<?>> extensions;
    protected Map<String, Object> fields;

    /**
     * Returns the fields (and their values) defined for this object
     */
    public Map<String, Object> getFields() {
        return fields;
    }
    /**
     * Sets the fields of this object
     */
    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }

    /**
     * Returns the (typed) field of this object
     * @param name  The name of the field
     * @param <T>   The type of the field
     */
    public <T> T getField(String name) {
        return this.fields != null ? (T) this.fields.get(name) : null;
    }

    /**
     * Adds a new field value to this object
     * @param name  The name of the field
     * @param value The value of the field
     */
    public void addField(String name, Object value) {
        if (this.fields == null) {
            this.fields = new HashMap<>();
        }
        this.fields.put(name, value);
    }
    /**
     * Returns the extensions associated to this object
     */
    public Map<ExtensionType, Extension<?>> getExtensions() {
        return extensions;
    }

    /**
     * Register a known extension with this object
     * @param type  The type of the extension
     */
    public Extension<?> registerExtension(ExtensionType type) {
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        Extension<Extensible> extension = ExtensionFactory.create(this, type);
        this.extensions.put(type, extension);
        return extension;
    }

    /**
     * Returns the extension of the requested type registered with this object,
     * or <code>null</code> if no such extension is registered with the instance.
     * @param extensionType     The extension type.
     * @param <T>   The Java type of the extension
     */
    public <T extends Extension<?>> T getExtension(ExtensionType extensionType) {
        return extensions != null ? (T) extensions.get(extensionType) : null;
    }
}
