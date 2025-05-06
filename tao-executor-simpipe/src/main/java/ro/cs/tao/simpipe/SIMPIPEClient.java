package ro.cs.tao.simpipe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import ro.cs.tao.argo.workflow.model.ArgoWorkflow;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.execution.ExecutionException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class SIMPIPEClient {

    private final Logger logger = Logger.getLogger(getClass().getName());
    private final String projectName;
    private final ArgoWorkflow argoTemplate;
    private final String graphQLURL;
    private final List<SIMPIPEArtifact> artifacts;
    private final int MAX_RETRIES = 7;
    private final int TIEMOUT = 60; // 5 minutes
    private String projectId;
    private String dryRunId;

    record SIMPIPEArtifact(String name, String url, String size, String bucketName) {
    }

    record SIMPIPEBucket(String name){
    }

    public SIMPIPEClient(String projectName, Path argoYaml) {
        this.projectName = constructProjectName(projectName);
        this.graphQLURL = ConfigurationManager.getInstance().getValue("tao.sintef.graphql");
        this.argoTemplate = parseYaml(argoYaml);
        this.artifacts = new ArrayList<>();
    }

    private String constructProjectName(String projectName) {
        return projectName.replaceAll("([-/:,]*)", "").replace(" ", "").replace("_", "-");
    }

    private JsonNode graphqlRequest(String query, Map<String, Object> variables, String operationName) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> payload = Map.of(
                "query", query,
                "variables", variables != null ? variables : Map.of(),
                "operationName", operationName
        );
        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(graphQLURL))
                .timeout(Duration.of(TIEMOUT, ChronoUnit.SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        int tries = 0;
        try(HttpClient httpClient = HttpClient.newHttpClient()) {
            while (tries < MAX_RETRIES) {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return objectMapper.readTree(response.body());
                }
                TimeUnit.SECONDS.sleep(5);
                tries++;
            }
        }
        logger.fine("GraphQL request failed after max retries.");
        throw new ExecutionException("GraphQL request failed after max retries.");
    }

    private void createProject() {
        String createProjectMutation = """
            mutation createProject($project: CreateProjectInput!) {
                createProject(project: $project) {
                    name
                    id
                }
            }
        """;
        Map<String, Object> variables = Map.of("project", Map.of("name", projectName));

        try {
            JsonNode response = graphqlRequest(createProjectMutation, variables, "createProject");
            JsonNode projectNode = response.path("data").path("createProject");
            if (projectNode.isMissingNode() || !projectNode.has("id"))
                throw new ExecutionException("Error creating project as no information has been returned!"
                        + projectNode.get("errors"));
            projectId = projectNode.get("id").asText();
        } catch (Exception e) {
            throw new ExecutionException(e.getMessage(), e.getCause());
        }
    }

    private void addArgoWorkflowToProject(){
        String addArgoWorkflowMutation = """
        mutation Mutation($input: CreateWorkflowTemplateInput!) {
            createWorkflowTemplate(input: $input) {
                name
            }
        }
        """;
        Map<String, Object> variables = Map.of("input", Map.of(
                "argoWorkflowTemplate", argoTemplate,
                "name", projectName,
                "projectId", projectId
                )
        );
        try {
            JsonNode response = graphqlRequest(addArgoWorkflowMutation, variables, "Mutation");
            JsonNode workflowTemplateNode = response.path("data").path("createWorkflowTemplate");
            if (workflowTemplateNode.isMissingNode() || !workflowTemplateNode.has("name"))
                throw new ExecutionException("Error adding Argo Workflow Template to project as no information has been returned!"
                        + workflowTemplateNode.get("errors"));
        } catch (Exception e) {
            throw new ExecutionException(e.getMessage(), e.getCause());
        }


    }

    private void executeDryRun() {
        //getBuckets();
        String executeDryRunMutation = """
            mutation createDryRun($input: CreateDryRunInput!) {
                createDryRun(input: $input) {
                    id
                    status {
                      phase
                      message
                      progress
                      estimatedDuration
                    }
                }
            }
        """;
        Map<String, Object> variables = Map.of("input", Map.of(
                "argoWorkflow", argoTemplate
                ,"projectId", projectId)
        );

        try {
            JsonNode response = graphqlRequest(executeDryRunMutation, variables, "createDryRun");
            JsonNode dryRunNode = response.path("data").path("createDryRun");
            if(dryRunNode.isMissingNode() || !dryRunNode.has("id"))
                throw new ExecutionException("Error executing Dry Run as no information has been returned!" + dryRunNode.get("errors"));
            this.dryRunId = dryRunNode.get("id").asText();
        } catch (Exception e) {
            throw new ExecutionException(e.getMessage(), e.getCause());
        }
    }

    private void getBuckets(){
        String getBucketsQuery = """
            query Buckets {
                buckets {
                    name
                }
            }
        """;
        try {
            JsonNode response = graphqlRequest(getBucketsQuery, null, "Buckets");
            ArrayNode bucketsNode = (ArrayNode) response.path("data").path("buckets");
            if (bucketsNode.isMissingNode())
                throw new ExecutionException("Error getting Buckets as no information has been returned!"
                        + bucketsNode.get("errors"));
            Iterator<JsonNode> iterator = bucketsNode.elements();
            SIMPIPEBucket bucket;
            while(iterator.hasNext()){
                bucket = new ObjectMapper().readValue(iterator.next().asText(), SIMPIPEBucket.class);
                getArtifacts(bucket.name());
            }
        } catch (Exception e) {
            throw new ExecutionException(e.getMessage(), e.getCause());
        }
    }

    private void getArtifacts(String bucketName){
        String getArtifactsQuery = """
            query Artifacts($bucketName: String) {
                artifacts(bucketName: $bucketName){
                    name		
                    url		
                    size			
                    bucketName
                }
             }
        """;
        Map<String, Object> variables = Map.of("bucketName", bucketName);
        try {

            JsonNode response = graphqlRequest(getArtifactsQuery, variables, "Artifacts");
            ArrayNode artifactsNode = (ArrayNode) response.path("data").path("artifacts");
            if (artifactsNode.isMissingNode())
                throw new ExecutionException("Error getting Artifacts as no information has been returned!"
                        + artifactsNode.get("errors"));
            Iterator<JsonNode> iterator = artifactsNode.elements();
            SIMPIPEArtifact artifact;
            while(iterator.hasNext()){
                artifact = new ObjectMapper().readValue(iterator.next().asText(), SIMPIPEArtifact.class);
                this.artifacts.add(artifact);
            }
        } catch (Exception e) {
            throw new ExecutionException(e.getMessage(), e.getCause());
        }
    }

    private void getDryRunMetrics() {
        String dryRunMetricsQuery = """
            query getDryRunMetrics($dryRunId: String!) {
                dryRun(dryRunId: $dryRunId) {			
                status {				
                    phase				
                    message			
                }			
                nodes {				
                    duration			
                    phase			
                    type				
                    ... on DryRunNodePod {					
                        displayName					
                        startedAt					
                        finishedAt					
                        type					
                        duration
                        progress					
                        log					
                        metrics {						
                            cpuUsageSecondsTotal {	
                                timestamp							
                                value						
                            }						
                            memoryUsageBytes {							
                                timestamp							
                                value						
                            }						
                            networkReceiveBytesTotal {					
                                timestamp							
                                value						
                            }						
                            networkTransmitBytesTotal {				
                                timestamp							
                                value		             				
                            }					
                        }				
                    }		
                }		
            }
        }
        """;
        Map<String, Object> variables = Map.of("dryRunId", dryRunId);

        try {
            TimeUnit.SECONDS.sleep(10);
            JsonNode response = graphqlRequest(dryRunMetricsQuery, variables, "getDryRunMetrics");
            JsonNode dryRunMetricsNode = response.path("data");
            if (dryRunMetricsNode.isMissingNode())
                throw new ExecutionException("Error getting Dry Run Metrics as no information has been returned!"
                        + dryRunMetricsNode.get("errors"));
            //TODO save the metrics
        } catch (Exception e) {
            throw new ExecutionException(e.getMessage(), e.getCause());
        }
    }

    public void runClient(){
        createProject();
        addArgoWorkflowToProject();
        executeDryRun();
        getDryRunMetrics();
    }

    private ArgoWorkflow parseYaml(Path file) {
        try {
            return new ObjectMapper(new YAMLFactory()).readValue(file.toFile(), ArgoWorkflow.class);
        } catch (Exception e) {
            logger.fine("Error in reading YAML Argo to an Argo Workflow" + e.getMessage());
        }
        return null;
    }
}
