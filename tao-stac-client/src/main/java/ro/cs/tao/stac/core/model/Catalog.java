package ro.cs.tao.stac.core.model;

import java.util.ArrayList;
import java.util.List;

public class Catalog {
    protected String stac_version;
    protected List<Object> stac_extensions;
    protected String type;
    protected String id;
    protected String title;
    protected String description;
    protected List<Link> links;

    public String getStac_version() {
        return stac_version;
    }

    public void setStac_version(String stac_version) {
        this.stac_version = stac_version;
    }

    public List<Object> getStac_extensions() {
        if (this.stac_extensions == null) {
            this.stac_extensions = new ArrayList<>();
        }
        return stac_extensions;
    }

    public void setStac_extensions(List<Object> stac_extensions) {
        this.stac_extensions = stac_extensions;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<Link> getLinks() {
        if (this.links == null) {
            this.links = new ArrayList<>();
        }
        return links;
    }

    public void setLinks(List<Link> links) {
        this.links = links;
    }
}
