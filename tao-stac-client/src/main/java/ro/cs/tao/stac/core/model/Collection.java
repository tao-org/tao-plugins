package ro.cs.tao.stac.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Collection {
    protected String stac_version;
    protected List<Object> stac_extensions;
    protected String type;
    protected String id;
    protected String title;
    protected String description;
    protected List<String> keywords;
    protected String license;
    protected Extent extent;
    protected List<Link> links;
    protected Map<String, Object> summaries;

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

    public List<String> getKeywords() {
        if (this.keywords == null) {
            this.keywords = new ArrayList<>();
        }
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public Extent getExtent() {
        return extent;
    }

    public void setExtent(Extent extent) {
        this.extent = extent;
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

    public Map<String, Object> getSummaries() {
        if (this.summaries == null) {
            this.summaries = new HashMap<>();
        }
        return summaries;
    }

    public void setSummaries(Map<String, Object> summaries) {
        this.summaries = summaries;
    }
}
