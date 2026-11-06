package ro.cs.tao.utils.cwl;

import org.w3id.cwl.cwl1_2.Workflow;
import org.w3id.cwl.cwl1_2.WorkflowStep;
import org.w3id.cwl.cwl1_2.utils.CWLGraphElement;
import org.w3id.cwl.cwl1_2.utils.CWLSerializer;
import org.w3id.cwl.cwl1_2.utils.Saveable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

import static ro.cs.tao.utils.cwl.CWLConstants.CWL_GRAPH_ID;

/** A packed CWL graph with a document-aware reference index. */
public class CWLTemplate {
    private String cwlVersion;
    private Map<String, LinkedList<Object>> cwlMainGraphObject = new LinkedHashMap<>();
    private final Map<String, String> references = new LinkedHashMap<>();
    private final Map<Object, String> documents = new IdentityHashMap<>();
    private final Set<String> usedIds = new HashSet<>();
    private final Map<String, Integer> documentElements = new HashMap<>();

    public String getCwlVersion() { return cwlVersion; }
    public void setCwlVersion(String value) { cwlVersion = value; }
    public Map<String, LinkedList<Object>> getCwlMainGraphObject() { return cwlMainGraphObject; }
    public void setCwlMainGraphObject(Map<String, LinkedList<Object>> value) { cwlMainGraphObject = value; }
    public void setCwlMainGraphObject(String key) {
        cwlMainGraphObject = new LinkedHashMap<>();
        cwlMainGraphObject.put(key, new LinkedList<>());
        references.clear(); documents.clear(); usedIds.clear(); documentElements.clear();
    }
    public LinkedList<Object> getCwlMainGraphObject(String key) { return cwlMainGraphObject.get(key); }

    public Object getCwlGraphChild(String key, String id) {
        String resolved = references.getOrDefault(id, id);
        if (resolved != null && resolved.startsWith("#")) resolved = resolved.substring(1);
        for (Object child : cwlMainGraphObject.getOrDefault(key, new LinkedList<>())) {
            if (child instanceof CWLGraphElement element && element.getId() != null
                    && element.getId().isPresent() && element.getId().get().equals(resolved)) return child;
        }
        return null;
    }

    public void addAllElements(Collection<?> elements, String document) throws IllegalAccessException {
        for (Object element : elements) addElement(element, document);
    }

    public void addElement(Object object, String document) throws IllegalAccessException {
        if (!(object instanceof CWLGraphElement element)) {
            throw new IllegalArgumentException("Unsupported CWL graph element: " + object.getClass().getSimpleName());
        }
        String documentUri = document.contains("file:") ? document : Path.of(document).toAbsolutePath().normalize().toUri().toString();
        String original = element.getId() == null ? null : element.getId().orElse(null);
        String candidate = original == null || original.isBlank() ? CWLUtils.generateId(Path.of(URI.create(documentUri)).getFileName().toString()) : fragment(original);
        if (candidate.isBlank() || candidate.contains("file:")) candidate = CWLUtils.generateId(Path.of(URI.create(documentUri)).getFileName().toString());
        String id = candidate;
        int suffix = 2;
        while (!usedIds.add(id)) id = candidate + "__" + suffix++;
        if (original != null) references.put(original, id);
        references.put(documentUri + "#" + candidate, id);
        // An unfragmented document is unambiguous only when it contains one process.
        int count = documentElements.merge(documentUri, 1, Integer::sum);
        references.put(documentUri, count == 1 ? id : "");
        element.setId(Optional.of(id));
        documents.put(object, documentUri);
        normalize(object, original, documentUri, Collections.newSetFromMap(new IdentityHashMap<>()));
        cwlMainGraphObject.get(CWL_GRAPH_ID).add(object);
    }

    private static String fragment(String value) {
        int hash = value.indexOf('#');
        return hash >= 0 ? value.substring(hash + 1) : value;
    }
    private static String localId(String value) {
        String result = fragment(value);
        return result.substring(result.lastIndexOf('/') + 1);
    }
    private static Object reference(Object value, String owner) {
        if (value instanceof Collection<?> values) {
            List<Object> result = new ArrayList<>();
            for (Object item : values) result.add(reference(item, owner));
            return result;
        }
        if (!(value instanceof String text)) return value;
        text = fragment(text);
        String scope = owner == null ? "" : fragment(owner);
        if (!scope.isEmpty() && text.startsWith(scope + "/")) text = text.substring(scope.length() + 1);
        return text;
    }

    private void normalize(Object object, String owner, String document, Set<Object> visited) throws IllegalAccessException {
        object = CWLSerializer.unwrap(object);
        if (object == null || !visited.add(object)) return;
        if (object instanceof Collection<?> values) {
            for (Object item : values) normalize(item, owner, document, visited);
            return;
        }
        if (!(object instanceof Saveable)) return;
        for (Field field : object.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getName().endsWith("_")) continue;
            field.setAccessible(true);
            Object raw = field.get(object);
            Object value = CWLSerializer.unwrap(raw);
            if (value == null) continue;
            Object replacement = value;
            String name = field.getName();
            if (name.equals("id") && !(object instanceof CWLGraphElement)) replacement = localId(value.toString());
            else if (name.equals("source") || name.equals("outputSource")) replacement = reference(value, owner);
            else if (name.equals("out")) {
                List<Object> outputs = new ArrayList<>();
                for (Object item : (Collection<?>) value) outputs.add(item instanceof String ? localId(item.toString()) : item);
                replacement = outputs;
            }
            if (replacement != value) field.set(object, raw instanceof Optional<?> ? Optional.of(replacement) : replacement);
            normalize(replacement, owner, document, visited);
        }
    }

    /** Resolves every run using its originating document, never a basename or first graph ID. */
    public void resolveRuns() {
        for (Object object : cwlMainGraphObject.get(CWL_GRAPH_ID)) {
            if (!(object instanceof Workflow workflow)) continue;
            for (Object item : workflow.getSteps()) {
                WorkflowStep step = (WorkflowStep) item;
                if (!(step.getRun() instanceof String run)) {
                    throw new IllegalArgumentException("Inline run definitions are not supported; use a named graph element.");
                }
                String resolved = references.get(run);
                if (resolved == null) {
                    String uri = URI.create(documents.get(object)).resolve(run).toString();
                    resolved = references.get(uri);
                }
                if (resolved == null && getCwlGraphChild(CWL_GRAPH_ID, run) != null) resolved = run.startsWith("#") ? run.substring(1) : run;
                if (resolved == null || resolved.isEmpty()) throw new IllegalArgumentException("Unresolved or ambiguous CWL run: " + run);
                step.setRun(resolved);
            }
        }
    }
    public void replaceRunFilesWithIds(Map<String, List<String>> ignored) { resolveRuns(); }

    public Map<Object, Object> save() {
        Map<Object, Object> result = new LinkedHashMap<>();
        result.put("cwlVersion", cwlVersion);
        cwlMainGraphObject.forEach((key, elements) -> {
            List<Object> graph = new ArrayList<>();
            for (Object element : elements) graph.add(CWLSerializer.serialize(element));
            result.put(key, graph);
        });
        return result;
    }
}
