package ro.cs.tao.stac.core.model;

public class Feature {
    protected ItemType type;
    protected Geometry<?> geometry;
    protected Object properties;

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public Geometry<?> getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry<?> geometry) {
        this.geometry = geometry;
    }

    public Object getProperties() {
        return properties;
    }

    public void setProperties(Object properties) {
        this.properties = properties;
    }
}
