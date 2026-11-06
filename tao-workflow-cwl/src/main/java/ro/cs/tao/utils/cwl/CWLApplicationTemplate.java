package ro.cs.tao.utils.cwl;

import java.util.Map;

public class CWLApplicationTemplate {
    private String applicationName;
    private String componentId;
    public String getComponentId() { return componentId; }
    public void setComponentId(String value) { componentId = value; }
    private String applicationPath;
    private Map<String, CWLParamDescTemplate> applicationParams;

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationPath() {
        return applicationPath;
    }

    public void setApplicationPath(String applicationPath) {
        this.applicationPath = applicationPath;
    }

    public Map<String, CWLParamDescTemplate> getApplicationParams() {
        return applicationParams;
    }

    public void setApplicationParams(Map<String, CWLParamDescTemplate> applicationParams) {
        this.applicationParams = applicationParams;
    }
}