package ro.cs.tao.opensearch.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "Parameter", namespace = "param")
public class Parameter {
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    private String name;
    @JacksonXmlProperty(isAttribute = true, localName = "value")
    private String value;
    @JacksonXmlProperty(isAttribute = true, localName = "minimum")
    private int minimum;
    @JacksonXmlProperty(isAttribute = true, localName = "minIclusive")
    private int minInclusive;
    @JacksonXmlProperty(isAttribute = true, localName = "maxExclusive")
    private int maxExclusive;
    @JacksonXmlProperty(isAttribute = true, localName = "title")
    private String title;
    private Link link;
    @JacksonXmlProperty(isAttribute = true, localName = "allowed")
    private boolean setAllowed;
    private List<Option> option;
    @JacksonXmlProperty(isAttribute = true, localName = "uiDisplay")
    private String uiDisplay;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getMinimum() {
        return minimum;
    }

    public void setMinimum(int minimum) {
        this.minimum = minimum;
    }

    public int getMinInclusive() {
        return minInclusive;
    }

    public void setMinInclusive(int minInclusive) {
        this.minInclusive = minInclusive;
    }

    public int getMaxExclusive() {
        return maxExclusive;
    }

    public void setMaxExclusive(int maxExclusive) {
        this.maxExclusive = maxExclusive;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Link getLink() {
        return link;
    }

    public void setLink(Link link) {
        this.link = link;
    }

    public boolean isSetAllowed() {
        return setAllowed;
    }

    public void setSetAllowed(boolean setAllowed) {
        this.setAllowed = setAllowed;
    }

    public List<Option> getOption() {
        return option;
    }

    public void setOption(List<Option> option) {
        this.option = option;
    }

    public String getUiDisplay() {
        return uiDisplay;
    }

    public void setUiDisplay(String uiDisplay) {
        this.uiDisplay = uiDisplay;
    }
}
