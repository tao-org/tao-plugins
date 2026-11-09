package ro.cs.tao.opensearch.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "Option", namespace = "param")
public class Option {
    @JacksonXmlProperty(isAttribute = true, localName = "value")
    private String value;
    @JacksonXmlProperty(isAttribute = true, localName = "label")
    private String label;
    @JacksonXmlProperty(isAttribute = true, localName = "results")
    private int results;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getResults() {
        return results;
    }

    public void setResults(int results) {
        this.results = results;
    }
}
