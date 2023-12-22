package ro.cs.tao.wms.beans;

import java.util.ArrayList;
import java.util.List;

public class LayerInfo {
    private String name;
    private String description;
    private String[] formats;
    private double[][] boundingBox;
    private String crs;
    private List<String> styles;
    private String wmsVersion;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String[] getFormats() {
        return formats;
    }

    public void setFormats(String[] formats) {
        this.formats = formats;
    }

    public double[][] getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(double[][] boundingBox) {
        this.boundingBox = boundingBox;
    }

    public String getCrs() {
        return crs;
    }

    public void setCrs(String crs) {
        this.crs = crs;
    }

    public List<String> getStyles() {
        return styles;
    }

    public void setStyles(List<String> styles) {
        this.styles = styles;
    }

    public void addStyle(String styleName) {
        if (this.styles == null) {
            this.styles = new ArrayList<>();
        }
        this.styles.add(styleName);
    }

    public String getWmsVersion() {
        return wmsVersion;
    }

    public void setWmsVersion(String wmsVersion) {
        this.wmsVersion = wmsVersion;
    }
}
