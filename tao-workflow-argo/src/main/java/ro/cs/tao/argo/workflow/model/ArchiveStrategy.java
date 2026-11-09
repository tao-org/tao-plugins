package ro.cs.tao.argo.workflow.model;

import java.util.HashMap;
import java.util.Map;

public class ArchiveStrategy {

    private Map<String, String> none = new HashMap<>();

    public Map<String, String> getNone() {
        return none;
    }

    public void addNone(String key, String value) {
        this.none.put(key, value);
    }
}
