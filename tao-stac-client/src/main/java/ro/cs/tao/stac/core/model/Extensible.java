package ro.cs.tao.stac.core.model;

import ro.cs.tao.stac.core.model.extensions.Extension;
import ro.cs.tao.stac.core.model.extensions.ExtensionFactory;
import ro.cs.tao.stac.core.model.extensions.ExtensionType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for extensible objects. It defines methods for getting/setting fields of the extensible object
 * and for registering extensions (extending the object).
 *
 * @author Cosmin Cara
 */
public class Extensible {
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String KEYWORDS = "keywords";
    public static final String ROLES = "roles";
    public static final String DATETIME = "datetime";
    public static final String CREATED = "datetime";
    public static final String UPDATED = "datetime";
    public static final String START_DATETIME = "start_datetime";
    public static final String END_DATETIME = "end_datetime";
    public static final String LICENSE = "license";
    public static final String PROVIDERS = "providers";
    public static final String INSTRUMENT = "instrument";
    public static final String BANDS = "bands";
    public static final String NODATA = "nodata";
    public static final String DATA_TYPE = "data_type";
    public static final String STATISTICS = "statistics";
    public static final String UNIT = "unit";
    
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
    
    public String getTitle() { return getField(TITLE); }
    public void setTitle(String title) { addField(TITLE, title); }
    
    public String getDescription() { return getField(DESCRIPTION); }
    public void setDescription(String description) { addField(DESCRIPTION, description); }
    
    public List<String> getKeywords() { return getField(KEYWORDS); }
    public void setKeywords(List<String> keywords) { addField(KEYWORDS, keywords); }
    
    public List<String> getRoles() { return getField(ROLES); }
    public void setRoles(List<String> roles) { addField(ROLES, roles); }
    
    public LocalDateTime getDatetime() { return getField(DATETIME); }
    public void setDatetime(LocalDateTime datetime) { addField(DATETIME, datetime); }
    
    public LocalDateTime getCreated() { return getField(CREATED); }
    public void setCreated(LocalDateTime created) { addField(CREATED, created); }
    
    public LocalDateTime getUpdated() { return getField(UPDATED); }
    public void setUpdated(LocalDateTime updated) { addField(UPDATED, updated); }
    
    public LocalDateTime getStart_datetime() { return getField(START_DATETIME); }
    public void setStart_datetime(LocalDateTime start_datetime) { addField(START_DATETIME, start_datetime); }
    
    public LocalDateTime getEnd_datetime() { return getField(END_DATETIME); }
    public void setEnd_datetime(LocalDateTime end_datetime) { addField(END_DATETIME, end_datetime);}

    public String getLicense() {
        return getField(LICENSE);
    }

    public void setLicense(String license) {
        addField(LICENSE, license);
    }

    public List<Provider> getProviders() {
        return getField(PROVIDERS);
    }

    public void setProviders(List<Provider> providers) {
        addField(PROVIDERS, providers);
    }

    public Instrument getInstrument() {
        return getField(INSTRUMENT);
    }

    public void setInstrument(Instrument instrument) {
        addField(INSTRUMENT, instrument);
    }

    public Object getNodata() {
        return getField(NODATA);
    }

    public void setNodata(Object nodata) {
        addField(NODATA, nodata);
    }

    public String getDataType() {
        return getField(DATA_TYPE);
    }

    public void setDataType(String dataType) {
        addField(DATA_TYPE, dataType);
    }

    public Statistics getStatistics() {
        return getField(STATISTICS);
    }

    public void setStatistics(Statistics statistics) {
        addField(STATISTICS, statistics);
    }

    public String getUnit() {
        return getField(UNIT);
    }

    public void setUnit(String unit) {
        addField(UNIT, unit);
    }

}
