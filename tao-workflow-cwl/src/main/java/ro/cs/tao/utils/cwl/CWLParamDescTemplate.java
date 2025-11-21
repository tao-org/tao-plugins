package ro.cs.tao.utils.cwl;

import java.util.ArrayList;
import java.util.List;

public class CWLParamDescTemplate {
    private String paramType;
    private String formatType;
    private String paramName;
    private String paramPrefix;
    private int paramPosition;
    private boolean mandatory;
    private Object defaultValue;

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }

    public String getFormatType() {
        return formatType;
    }

    public void setFormatType(String formatType) {
        this.formatType = formatType;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getParamPrefix() {
        return paramPrefix;
    }

    public void setParamPrefix(String paramPrefix) {
        this.paramPrefix = paramPrefix;
    }

    public int getParamPosition() {
        return paramPosition;
    }

    public void setParamPosition(int paramPosition) {
        this.paramPosition = paramPosition;
    }
}
