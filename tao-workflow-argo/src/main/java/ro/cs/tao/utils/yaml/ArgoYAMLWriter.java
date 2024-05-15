package ro.cs.tao.utils.yaml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import ro.cs.tao.argo.workflow.model.ArgoWorkflow;
import ro.cs.tao.argo.workflow.model.WorkflowTemplate;
import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.persistence.ExecutionTaskProvider;
import ro.cs.tao.execution.util.TaskUtilities;
import ro.cs.tao.utils.executors.ExecutionUnit;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static ro.cs.tao.utils.constants.ArgoConstants.*;

public class ArgoYAMLWriter {

    public static void createYaml(ArgoWorkflow argoWorkflow, ExecutionUnit executionUnit) throws IOException {
        final Path path = Paths.get(executionUnit.getScriptTargetPath());
        ArgoWorkflowYAMLFactory factory =  new ArgoWorkflowYAMLFactory();
        factory.enable(ArgoWorkflowYAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(ArgoWorkflowYAMLGenerator.Feature.WRITE_DOC_START_MARKER);

        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            Map<String, Object> workflowMap = mapper.convertValue(argoWorkflow, new TypeReference<>() {
            });
            mapper.writeValue(path.toFile(), workflowMap);
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    public static void updateYaml(ArgoWorkflow argoWorkflow, ExecutionUnit executionUnit, String executionUnitName) throws IOException {
        final Path path = Paths.get(executionUnit.getScriptTargetPath());
        ArgoWorkflowYAMLFactory factory =  new ArgoWorkflowYAMLFactory();
        factory.enable(ArgoWorkflowYAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(ArgoWorkflowYAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        try {
            Map<String, Object> workflowMap= mapper.readValue(path.toFile(), Map.class);
            if (workflowMap != null && !workflowMap.isEmpty()) {
                List<LinkedHashMap<String,Object>> templates = (ArrayList<LinkedHashMap<String,Object>>)((LinkedHashMap<String, Object>) workflowMap.get("spec")).get("templates");
                Map<String, Object> templateFromFile = new LinkedHashMap<>();
                for(Map<String, Object> templateMap : templates){
                    String templateName = (String) templateMap.get("name");
                    if(templateName.contains(executionUnitName)){
                        templateFromFile = templateMap;
                    }
                }
                Optional<WorkflowTemplate> optionalTemplateFromArgoWorkflow = argoWorkflow.getSpec().getTemplates()
                        .stream()
                        .filter(template -> template.getName().contains(executionUnitName))
                        .findFirst();
                if(optionalTemplateFromArgoWorkflow.isEmpty())
                    throw new NullPointerException("No Template exists for the specified execution unit!");
                WorkflowTemplate templateFromArgo = optionalTemplateFromArgoWorkflow.get();
                Map<String, Object> containerMap = mapper.convertValue(templateFromArgo.getContainer(), new TypeReference<>() {
                });
                templateFromFile.put("container", containerMap);
                mapper.writeValue(path.toFile(), workflowMap);
            }
        } catch (IOException e) {
            throw new IOException(e);
        }
    }
}
