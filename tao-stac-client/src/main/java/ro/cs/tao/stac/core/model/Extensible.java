package ro.cs.tao.stac.core.model;

import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.model.extensions.ExtensionFactory;
import ro.cs.tao.stac.core.model.extensions.ExtensionType;

import java.util.HashMap;
import java.util.Map;

public class Extensible {
    protected Map<ExtensionType, Extension<?>> extensions;
    protected Map<String, Object> fields;

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }

    public <T> T getField(String name) {
        return this.fields != null ? (T) this.fields.get(name) : null;
    }

    public void addField(String name, Object value) {
        if (this.fields == null) {
            this.fields = new HashMap<>();
        }
        this.fields.put(name, value);
    }

    public Map<ExtensionType, Extension<?>> getExtensions() {
        return extensions;
    }

    public Extension<?> registerExtension(ExtensionType type) {
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        Extension<Extensible> extension = ExtensionFactory.create(this, type);
        this.extensions.put(type, extension);
        return extension;
    }

    public <T extends Extension<?>> T getExtension(ExtensionType extensionType) {
        return extensions != null ? (T) extensions.get(extensionType) : null;
    }
}
