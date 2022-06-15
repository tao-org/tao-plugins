package ro.cs.tao.stac.core.model;

import java.util.List;

public class ItemCollection {
    protected String stac_version;
    protected ItemType type;
    protected List<Item> features;
    protected List<Link> links;
    protected int numberReturned;
    protected int numberMatched;
    protected PageContext context;

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public List<Item> getFeatures() {
        return features;
    }

    public void setFeatures(List<Item> features) {
        this.features = features;
    }

    public String getStac_version() {
        return stac_version;
    }

    public void setStac_version(String stac_version) {
        this.stac_version = stac_version;
    }

    public List<Link> getLinks() {
        return links;
    }

    public void setLinks(List<Link> links) {
        this.links = links;
    }

    public int getNumberReturned() {
        return numberReturned;
    }

    public void setNumberReturned(int numberReturned) {
        this.numberReturned = numberReturned;
    }

    public int getNumberMatched() {
        return numberMatched;
    }

    public void setNumberMatched(int numberMatched) {
        this.numberMatched = numberMatched;
    }

    public PageContext getContext() {
        return context;
    }

    public void setContext(PageContext context) {
        this.context = context;
    }
}
