package ro.cs.tao.execution.ogc.model;

import java.util.ArrayList;
import java.util.List;

public class ConformanceClasses {
    private List<String> conformsTo = new ArrayList<>();

    public List<String> getConformsTo() { return conformsTo; }
    public void setConformsTo(List<String> conformsTo) { this.conformsTo = conformsTo; }
}