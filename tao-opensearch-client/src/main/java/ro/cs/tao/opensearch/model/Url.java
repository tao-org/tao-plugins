package ro.cs.tao.opensearch.model;

import jakarta.xml.bind.annotation.XmlAttribute;
import ro.cs.tao.datasource.param.DataSourceParameter;

import java.util.ArrayList;
import java.util.List;

public class Url {
    private List<DataSourceParameter> parameters;
    private String type;
    private String rel;
    private String template;

    public List<DataSourceParameter> getParameters() {
        return parameters;
    }

    public void setParameter(List<DataSourceParameter> parameters) {
        this.parameters = parameters;
    }

    public void addParameter(DataSourceParameter parameter) {
        if (this.parameters == null) {
            this.parameters = new ArrayList<>();
        }
        this.parameters.add(parameter);
    }

    @XmlAttribute
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRel() {
        return rel;
    }

    public void setRel(String rel) {
        this.rel = rel;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }
}
