package ro.cs.tao.stac.model;

import java.util.List;
import java.util.Map;

public class Classification {

    private String href;
    private String type;
    private String title;
    private List<EOBand> eoBands = null;
    private Integer projEpsg;
    private List<Integer> projShape = null;
    private List<Float> projTransform = null;
    private List<String> roles = null;
    private Map<String, Object> additionalProperties;

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<EOBand> getEoBands() {
        return eoBands;
    }

    public void setEoBands(List<EOBand> eoBands) {
        this.eoBands = eoBands;
    }

    public Integer getProjEpsg() {
        return projEpsg;
    }

    public void setProjEpsg(Integer projEpsg) {
        this.projEpsg = projEpsg;
    }

    public List<Integer> getProjShape() {
        return projShape;
    }

    public void setProjShape(List<Integer> projShape) {
        this.projShape = projShape;
    }

    public List<Float> getProjTransform() {
        return projTransform;
    }

    public void setProjTransform(List<Float> projTransform) {
        this.projTransform = projTransform;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}