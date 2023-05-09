package ro.cs.tao.stac.core.model;

import java.util.List;

public class CollectionList {
    private String stac_version;
    private List<Link> links;
    private List<Collection> collections;

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

    public List<Collection> getCollections() {
        return collections;
    }

    public void setCollections(List<Collection> collections) {
        this.collections = collections;
    }
}
