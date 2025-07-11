package ro.cs.tao.utils.executors;

import org.w3id.cwl.cwl1_2.utils.*;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CWLTemplate {
    private String cwlVersion;
    private Map<String, LinkedList<Object>> cwlMainGraphObject;

    public CWLTemplate() {
        cwlMainGraphObject = new LinkedHashMap<>();
    }

    public String getCwlVersion() {
        return cwlVersion;
    }

    public void setCwlVersion(String cwlVersion) {
        this.cwlVersion = cwlVersion;
    }

    public Map<String, LinkedList<Object>> getCwlMainGraphObject() {
        return cwlMainGraphObject;
    }

    public void setCwlMainGraphObject(Map<String,LinkedList<Object>> cwlMainGraphObject) {
        this.cwlMainGraphObject = cwlMainGraphObject;
    }

    public void setCwlMainGraphObject(String key) {
        this.cwlMainGraphObject = new LinkedHashMap<>(Map.of(key, new LinkedList<>()));
    }

    public LinkedList<Object> getCwlMainGraphObject(String key) {
        return cwlMainGraphObject.get(key);
    }

    public Object getCwlGraphChild(String key, String id) {
        LinkedList<Object> graphChildren = cwlMainGraphObject.get(key);
        String idRegex = "(file:/|file://|file:///).*";
        for (Object graphChild : graphChildren) {
            if (graphChild instanceof CWLGraphElement) {
                String graphChildId = ((CWLGraphElement) graphChild).getId().get();
                if (graphChildId.matches(idRegex)) {
                    int chIdx = graphChildId.indexOf("#");
                    graphChildId = graphChildId.substring(chIdx + 1);
                }
                if (graphChildId.equals(id)) {
                    return graphChild;
                }
            }
        }
        return null;
    }

    public Map<Object, Object> save() {
        Map<Object, Object> workflow = new LinkedHashMap<>();
        List<Object> cwlGraphList = new LinkedList<>();
        workflow.put("cwlVersion", cwlVersion);
        for (String cwlGraphKey : cwlMainGraphObject.keySet()) {
            cwlGraphList.clear();
            for (Object cwlGraphObj : cwlMainGraphObject.get(cwlGraphKey)) {
                cwlGraphList.add(((SaveableImpl)cwlGraphObj).save());
            }
            workflow.put(cwlGraphKey, cwlGraphList);
        }
        return workflow;
    }
}
