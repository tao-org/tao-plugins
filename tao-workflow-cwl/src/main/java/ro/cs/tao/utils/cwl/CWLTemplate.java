package ro.cs.tao.utils.cwl;

import org.ggf.drmaa.JobTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.utils.*;

import java.lang.reflect.Field;
import java.util.*;

import static ro.cs.tao.utils.cwl.CWLConstants.CWL_GRAPH_ID;

/**
 * Represents a Common Workflow Language (CWL) template used to define
 * workflow structures and metadata. This class manages the CWL version and
 * the main workflow graph, and provides utility methods for adding,
 * retrieving and formatting CWL graph elements.
 *
 * <p>The class supports adding individual or multiple graph elements,
 * retrieving child nodes, "prettifying" field names and values for better
 * readability, and exporting the CWL graph structure in a storable format.</p>
 *
 */
public class CWLTemplate {
    private static final Logger log = LoggerFactory.getLogger(CWLTemplate.class);
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

    /**
     * Retrieves a child element from the CWL main graph
     * based on the provided key and identifier.
     *
     * @param key the graph key
     * @param id the identifier of the child element
     * @return the corresponding graph child object, or null if not found
     */
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

    /**
     * Adds a single element to the CWL main graph.
     * This method perform field formatting before insertion.
     *
     * @param graphElem the CWL graph element
     * @param currentCwlFile the name of the current CWL file
     */
    public void addElement(Object graphElem, String currentCwlFile) throws IllegalAccessException {
        for (Field field : graphElem.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (!field.getName().endsWith("_") && field.get(graphElem) != null) {
                if (!(field.get(graphElem) instanceof Optional<?>) && field.get(graphElem).getClass().getDeclaredFields().length > 0) {
                    prettifyFields(field.get(graphElem), currentCwlFile);
                } else {
                    prettifyField(graphElem, field, currentCwlFile);
                }
            }
        }
        if (graphElem instanceof Workflow) {
            if (((Workflow) graphElem).getId() == null || ((Workflow) graphElem).getId().isEmpty()
                    || ((Workflow) graphElem).getId().get().isEmpty()) {
                String generatedId = CWLUtils.generateId(currentCwlFile);
                ((Workflow)graphElem).setId(Optional.of(generatedId));
            }
            prettifyStepFields(((Workflow) graphElem).getId().get(), ((Workflow) graphElem).getSteps());
        }
        // add id based on file name for processing purposes
        if (graphElem instanceof CommandLineTool) {
            if(((CommandLineTool) graphElem).getId().get().isEmpty()) {
                String generatedId = CWLUtils.generateId(currentCwlFile);
                ((CommandLineTool) graphElem).setId(Optional.of(generatedId));
            }
        }
        getCwlMainGraphObject().get(CWL_GRAPH_ID).add(graphElem);
    }

    /**
     * Adds multiple CWL graph elements to the main graph.
     *
     * @param graphElements a list of CWL graph elements
     * @param currentCwlFile the name the current CWL file
     */
    public void addAllElements(Collection<?> graphElements, String currentCwlFile) throws IllegalAccessException {
        for (Object graphElem : graphElements) {
            addElement(graphElem, currentCwlFile);
        }
    }

    /**
     * Recursively formats all fields of a given CWL graph element.
     *
     * @param element the CWL graph element
     * @param currentCwlFile the name of the current CWL file
     */
    private void prettifyFields(Object element, String currentCwlFile) throws IllegalAccessException {
        if (element instanceof Collection<?>) {
            for(Object elem : ((Collection<?>) element).toArray()) {
                prettifyFields(elem, currentCwlFile);
            }
        } else {
            if (element instanceof String) {
                prettifyField(element, element.getClass().getDeclaredFields()[0], currentCwlFile);
            } else {
                for (Field field : element.getClass().getDeclaredFields()) {
                    if (!field.getName().endsWith("_")) {
                        prettifyField(element, field, currentCwlFile);
                    }
                }
            }
        }
    }

    /**
     * Formats a specific field of a CWL element.
     *
     * @param element the CWL element
     * @param field the field to be formatted
     * @param currentCwlFile the current CWL file
     */
    private void prettifyField (Object element, Field field, String currentCwlFile) throws IllegalAccessException {
        field.setAccessible(true);
        if (field.get(element) != null) {
            /* The second condition is for sources.
             * A source is defined as Object and can be String or instace of Collection.
             * But all other fields that are defined as Object must be ignored by this condition.
             */
            boolean isSourceArray = Collection.class.isAssignableFrom(field.get(element).getClass())
                    && ((ArrayList<?>) field.get(element)).stream().anyMatch(f -> f.toString().contains(currentCwlFile)
                    || f.toString().contains("file:/") || f.toString().contains("#"));
            if (Collection.class.isAssignableFrom(field.getType()) || isSourceArray) {
                if (((ArrayList<?>) field.get(element)).getFirst() instanceof String) {
                    List<String> prettified = new ArrayList<>();
                    for (Object item : ((ArrayList<?>) field.get(element))) {
                        if (item.toString().contains("file:/")) {
                            prettified.add(prettifyString(item.toString(), currentCwlFile, false));
                        } else {
                            prettified.add(item.toString());
                        }
                    }
                    field.set(element, prettified);
                } else {
                    prettifyFields(field.get(element), currentCwlFile);
                }
            } else {
                String val = field.get(element).toString();
                boolean optionalField = false;
                if (field.get(element) instanceof Optional<?>) {
                    val = ((Optional<?>) field.get(element)).get().toString();
                    optionalField = true;
                }
                if (val.contains("file:/")) {
                    val = prettifyString(val, currentCwlFile, field.getName().equals("id"));
                    field.set(element, optionalField ? Optional.of(val) : val);
                }
            }
        }
    }

    /**
     * Formats a string value.
     *
     * @param value the original string value
     * @param currentCwlFile the CWL element
     * @param isId true or false, depends if the value represents an identifier
     * @return the formatted string value
     */
    private String prettifyString(String value, String currentCwlFile, boolean isId) {
        if (isId) {
            value = value.substring(value.lastIndexOf("/") + 1);
        } else {
            int dotPosition = value.lastIndexOf(".");
            String tmpVal = value.substring(dotPosition + 1);
            if (tmpVal.contains("/")) {
                if (tmpVal.lastIndexOf("/") > tmpVal.indexOf("/")) {
                    value = tmpVal.substring(tmpVal.indexOf("/") + 1);
                }
            } else {
                value = value.substring(value.lastIndexOf("/") + 1);
            }
        }
        if (value.contains(currentCwlFile)) {
            if (value.matches("(^|.*/)"+currentCwlFile.replaceAll("\\.", "\\\\.")+".*")) {
                value = value.replaceAll(currentCwlFile, "");
            } else {
                System.out.println("For dev purposes: " + value + " contains current CWL file name " + currentCwlFile + " but its value is not modified");
            }
        }
        if(value.contains("#")) {
            value = value.substring(value.lastIndexOf("#") + 1);
        }
        return value;
    }

    /**
     * Prettifies the fields of CWL workflow steps.
     *
     * @param graphId the id of the workflow graph
     * @param steps the list of CWL steps to be processed
     */
    private void prettifyStepFields(String graphId, List<Object> steps) {
        for (Object step : steps) {
            ((WorkflowStep) step).getIn().stream()
                    .filter(in -> ((WorkflowStepInput) in).getSource() != null
                            && ((WorkflowStepInput) in).getSource().toString().contains(graphId))
                    .forEach(in -> {
                        String newSourceVal =  ((WorkflowStepInput) in).getSource().toString().replace(graphId + "/", "");
                        ((WorkflowStepInput) in).setSource(newSourceVal);
                    });
            if (((WorkflowStep) step).getId().isPresent() && !((WorkflowStep) step).getId().get().isEmpty()) {
                String stepId = ((WorkflowStep) step).getId().get();
                List<Object> outList = ((WorkflowStep) step).getOut();
                for (int idx = 0; idx < outList.size(); idx++) {
                    Object out = outList.get(idx);
                    if (out instanceof String && out.toString().contains(stepId)) {
                        outList.set(idx, out.toString().replace(stepId + "/", ""));
                    } else if (out instanceof WorkflowStepOutput){
                        if (((WorkflowStepOutput) out).getId().isPresent() && ((WorkflowStepOutput) out).getId().get().contains(graphId)) {
                            String newVal =  ((WorkflowStepOutput) out).getId().get().replace(stepId + "/", "");
                            ((WorkflowStepOutput) out).setId(Optional.of(newVal));
                        }
                    }
                }
            }
        }
    }

    /**
     * Replaces file references in CWL workflow steps with their corresponding
     * internal CWL id based on the provided mapping.
     *
     * @param roadMap a mapping of CWL file paths to lists of corresponding CWL ids
     */
    public void replaceRunFilesWithIds(Map<String,List<String>> roadMap) {
        getCwlMainGraphObject().get(CWL_GRAPH_ID).stream().filter(obj -> obj instanceof Workflow)
                .flatMap(wf -> ((Workflow) wf).getSteps().stream()).filter(step -> {
                    String runValue = ((WorkflowStep)step).getRun().toString();
                    return runValue.contains(".cwl");
                }).forEach(step -> ((WorkflowStep) step).setRun(roadMap.get(((WorkflowStep) step).getRun().toString()).getFirst()));
    }

    public Map<Object, Object> save() {
        Map<Object, Object> workflow = new LinkedHashMap<>();
        List<Object> cwlGraphList = new LinkedList<>();
        workflow.put("cwlVersion", cwlVersion);
        for (String cwlGraphKey : cwlMainGraphObject.keySet()) {
            cwlGraphList.clear();
            for (Object cwlGraphObj : cwlMainGraphObject.get(cwlGraphKey)) {
                cwlGraphList.add(((SaveableImpl) cwlGraphObj).save());
            }
            workflow.put(cwlGraphKey, cwlGraphList);
        }
        return workflow;
    }
}
