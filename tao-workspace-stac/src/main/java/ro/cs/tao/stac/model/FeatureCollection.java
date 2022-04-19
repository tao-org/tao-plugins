package ro.cs.tao.stac.model;

import java.util.List;
import java.util.Map;

public class FeatureCollection {

    private String stacVersion;
    private String type;
    private List<Feature> features = null;
    private List<Link> links = null;
    private Integer numberReturned;
    private Context context;
    private Integer numberMatched;
    private Map<String, Object> additionalProperties;

    public String getStacVersion() {
        return stacVersion;
    }

    public void setStacVersion(String stacVersion) {
        this.stacVersion = stacVersion;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(List<Feature> features) {
        this.features = features;
    }

    public List<Link> getLinks() {
        return links;
    }

    public void setLinks(List<Link> links) {
        this.links = links;
    }

    public Integer getNumberReturned() {
        return numberReturned;
    }

    public void setNumberReturned(Integer numberReturned) {
        this.numberReturned = numberReturned;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public Integer getNumberMatched() {
        return numberMatched;
    }

    public void setNumberMatched(Integer numberMatched) {
        this.numberMatched = numberMatched;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}