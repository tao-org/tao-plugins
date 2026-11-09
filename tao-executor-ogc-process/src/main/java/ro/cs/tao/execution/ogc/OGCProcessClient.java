package ro.cs.tao.execution.ogc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.execution.ogc.model.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Client for OGC API - Processes compliant services
 * Supports the OGC API - Processes 1.0 specification
 */
public class OGCProcessClient {
    private static final Logger logger = Logger.getLogger(OGCProcessClient.class.getName());
    private static final String MEDIA_TYPE_JSON = "application/json";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final WebServiceAuthentication authentication;

    public OGCProcessClient(String baseUrl) {
        this(baseUrl, null);
    }

    public OGCProcessClient(String baseUrl, WebServiceAuthentication authentication) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authentication = authentication;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                                    .connectTimeout(Duration.ofSeconds(30))
                                    .build();
    }

    /**
     * Get the landing page of the service
     */
    public CompletableFuture<LandingPage> getLandingPage() {
        return sendGetRequest("/", LandingPage.class);
    }

    /**
     * Get conformance classes supported by the service
     */
    public CompletableFuture<ConformanceClasses> getConformanceClasses() {
        return sendGetRequest("/conformance", ConformanceClasses.class);
    }

    /**
     * Get list of available processes
     */
    public CompletableFuture<ProcessList> getProcesses() {
        return sendGetRequest("/processes", ProcessList.class);
    }

    /**
     * Get detailed description of a specific process
     */
    public CompletableFuture<ProcessDescription> getProcessDescription(String processId) {
        return sendGetRequest("/processes/" + processId, ProcessDescription.class);
    }

    /**
     * Execute a process synchronously
     */
    public CompletableFuture<ExecutionResult> executeProcess(String processId, ExecuteRequest executeRequest) {
        return sendPostRequest("/processes/" + processId + "/execution", executeRequest, ExecutionResult.class);
    }

    /**
     * Execute a process asynchronously
     */
    public CompletableFuture<JobStatus> executeProcessAsync(String processId, ExecuteRequest executeRequest) {
        executeRequest.setResponse("document");
        executeRequest.setMode("async");

        return sendPostRequest("/processes/" + processId + "/execution", executeRequest, String.class)
                .thenCompose(location -> {
                    // Extract job ID from location header or response
                    return getJobStatus(extractJobId(location));
                });
    }

    /**
     * Get status of a job
     */
    public CompletableFuture<JobStatus> getJobStatus(String jobId) {
        return sendGetRequest("/jobs/" + jobId, JobStatus.class);
    }

    /**
     * Get results of a completed job
     */
    public CompletableFuture<ExecutionResult> getJobResults(String jobId) {
        return sendGetRequest("/jobs/" + jobId + "/results", ExecutionResult.class);
    }

    /**
     * Get list of jobs
     */
    public CompletableFuture<JobList> getJobs() {
        return sendGetRequest("/jobs", JobList.class);
    }

    /**
     * Cancel a job
     */
    public CompletableFuture<Void> cancelJob(String jobId) {
        return sendDeleteRequest("/jobs/" + jobId);
    }

    private <T> CompletableFuture<T> sendGetRequest(String path, Class<T> responseType) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                                            .uri(URI.create(baseUrl + path))
                                                            .header("Accept", MEDIA_TYPE_JSON)
                                                            .GET();

            addAuthentication(requestBuilder);

            HttpRequest request = requestBuilder.build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                             .thenApply(response -> {
                                 if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                     try {
                                         return objectMapper.readValue(response.body(), responseType);
                                     } catch (Exception e) {
                                         throw new RuntimeException("Failed to parse response", e);
                                     }
                                 } else {
                                     throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                                 }
                             });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private <T, R> CompletableFuture<R> sendPostRequest(String path, T requestBody, Class<R> responseType) {
        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                                            .uri(URI.create(baseUrl + path))
                                                            .header("Content-Type", MEDIA_TYPE_JSON)
                                                            .header("Accept", MEDIA_TYPE_JSON)
                                                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            addAuthentication(requestBuilder);

            HttpRequest request = requestBuilder.build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                             .thenApply(response -> {
                                 if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                     try {
                                         if (responseType == String.class) {
                                             // For async execution, return location header or body
                                             String location = response.headers().firstValue("Location").orElse(response.body());
                                             return responseType.cast(location);
                                         }
                                         return objectMapper.readValue(response.body(), responseType);
                                     } catch (Exception e) {
                                         throw new RuntimeException("Failed to parse response", e);
                                     }
                                 } else {
                                     throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                                 }
                             });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<Void> sendDeleteRequest(String path) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                                            .uri(URI.create(baseUrl + path))
                                                            .DELETE();

            addAuthentication(requestBuilder);

            HttpRequest request = requestBuilder.build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                             .thenApply(response -> {
                                 if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                     return null;
                                 } else {
                                     throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                                 }
                             });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void addAuthentication(HttpRequest.Builder requestBuilder) {
        if (authentication != null) {
            switch (authentication.getType()) {
                case BASIC:
                    String credentials = Base64.getEncoder()
                                               .encodeToString((authentication.getUser() + ":" + authentication.getPassword())
                                                                       .getBytes());
                    requestBuilder.header("Authorization", "Basic " + credentials);
                    break;
                case TOKEN:
                    requestBuilder.header(authentication.getAuthHeader(), getToken());
                    break;
            }
        }
    }

    private String getToken() {
        if (authentication != null && authentication.getLoginUrl() != null) {
            try {
                // Implement token retrieval logic similar to WPSHttpClient
                HttpRequest loginRequest = HttpRequest.newBuilder()
                                                      .uri(URI.create(authentication.getLoginUrl()))
                                                      .header("Content-Type", "application/x-www-form-urlencoded")
                                                      .POST(HttpRequest.BodyPublishers.ofString(
                                                              "user=" + URLEncoder.encode(authentication.getUser(), "UTF-8") +
                                                              "&password=" + URLEncoder.encode(authentication.getPassword(), "UTF-8")))
                                                      .build();

                HttpResponse<String> response = httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode jsonResponse = objectMapper.readTree(response.body());
                    JsonNode data = jsonResponse.get("data");
                    if (data.isTextual()) {
                        return data.asText();
                    } else {
                        // Look for token field in the data object
                        Iterator<Map.Entry<String, JsonNode>> fields = data.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            if (field.getKey().toLowerCase().contains("token")) {
                                return field.getValue().asText();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to retrieve authentication token: " + e.getMessage());
            }
        }
        return null;
    }

    private String extractJobId(String locationOrResponse) {
        // Extract job ID from location URL or response body
        if (locationOrResponse.contains("/jobs/")) {
            String[] parts = locationOrResponse.split("/jobs/");
            if (parts.length > 1) {
                return parts[1].split("/")[0];
            }
        }
        return locationOrResponse;
    }
}