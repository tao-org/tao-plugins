package ro.cs.tao.stac.core.model;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class Item extends Extensible {
    public static final String DATETIME = "datetime";
    private static final Set<String> declaredFieldNames;

    static {
        final Field[] declaredFields = Item.class.getDeclaredFields();
        declaredFieldNames = Arrays.stream(declaredFields).map(Field::getName).collect(Collectors.toSet());
        declaredFieldNames.add(DATETIME);
    }

    protected String stac_version;
    protected List<String> stac_extensions;
    protected String id;
    protected double[] bbox;
    protected Geometry geometry;
    protected ItemType type;
    protected List<Link> links;
    protected Map<String, Asset> assets;

    public static Set<String> coreFieldNames(){
        return declaredFieldNames;
    }

    public String getStac_version() {
        return stac_version;
    }

    public void setStac_version(String stac_version) {
        this.stac_version = stac_version;
    }

    public List<String> getStac_extensions() {
        return stac_extensions;
    }

    public void setStac_extensions(List<String> stac_extensions) {
        this.stac_extensions = stac_extensions;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double[] getBbox() {
        return bbox;
    }

    public void setBbox(double[] bbox) {
        this.bbox = bbox;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public List<Link> getLinks() {
        return links;
    }

    public void setLinks(List<Link> links) {
        this.links = links;
    }

    public void addLink(Link link) {
        if (this.links == null) {
            this.links = new ArrayList<>();
        }
        this.links.add(link);
    }

    public Map<String, Asset> getAssets() {
        return assets;
    }

    public void setAssets(Map<String, Asset> assets) {
        this.assets = assets;
    }

    public void addAsset(String name, Asset asset) {
        if (this.assets == null) {
            this.assets = new HashMap<>();
        }
        this.assets.put(name, asset);
    }

    public LocalDateTime getDatetime() {
        return getField(DATETIME);
    }

    public void setDatetime(LocalDateTime datetime) {
        addField(DATETIME, datetime);
    }
}
