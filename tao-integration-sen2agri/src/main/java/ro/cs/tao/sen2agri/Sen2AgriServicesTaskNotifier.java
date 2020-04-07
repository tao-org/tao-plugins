package ro.cs.tao.sen2agri;

import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicNameValuePair;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.execution.model.ExternalTaskListener;
import ro.cs.tao.execution.model.ExternalTaskListenerDescriptor;
import ro.cs.tao.execution.model.ProcessingExecutionTask;
import ro.cs.tao.persistence.WorkflowNodeProvider;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.workflow.ParameterValue;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Sen2AgriServicesTaskNotifier implements ExternalTaskListener {
    private final WorkflowNodeProvider nodeProvider;
    private ExternalTaskListenerDescriptor descriptor;
    private final Logger logger;

    public Sen2AgriServicesTaskNotifier() {
        logger = Logger.getLogger(Sen2AgriServicesTaskNotifier.class.getName());
        nodeProvider = SpringContextBridge.services().getService(WorkflowNodeProvider.class);
        final ConfigurationProvider cfgManager = ConfigurationManager.getInstance();
        final String hostName = cfgManager.getValue(Constants.SEN2AGRI_SERVICES_HOSTNAME);
        final int port = Integer.parseInt(cfgManager.getValue(Constants.SEN2AGRI_SERVICES_PORT, "-1"));
        if (hostName != null && port > 0) {
            final String endpoint = cfgManager.getValue(Constants.SEN2AGRI_SERVICES_ENDPOINT);
            if (StringUtilities.isNullOrEmpty(endpoint)) {
                logger.severe("Sen2Agri-Services host and port are configured, but no endpoint is defined");
            } else {
                final String user = cfgManager.getValue(Constants.SEN2AGRI_SERVICES_USER);
                final String pwd = cfgManager.getValue(Constants.SEN2AGRI_SERVICES_PASSWORD);
                Credentials credentials = null;
                if (!StringUtilities.isNullOrEmpty(user) && !StringUtilities.isNullOrEmpty(pwd)) {
                    credentials = new UsernamePasswordCredentials(user, pwd);
                }
                this.descriptor = new ExternalTaskListenerDescriptor(hostName, port, endpoint, credentials);
                logger.fine(String.format("Sen2Agri-Services callback configured at https://%s:%d%s",
                                          hostName, port, endpoint));
            }
        } else {
            logger.warning("Sen2Agri-Services callback configuration not found");
        }
    }

    @Override
    public void setDescriptor(ExternalTaskListenerDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public ExternalTaskListenerDescriptor getDescriptor() { return this.descriptor; }

    @Override
    public void onStarted(ProcessingExecutionTask task) {
        if (this.descriptor == null) {
            throw new RuntimeException(("External listener descriptor not set"));
        }
        final List<ParameterValue> info = nodeProvider.get(task.getWorkflowNodeId()).getAdditionalInfo();
        if (info == null) {
            throw new IllegalArgumentException("Node doesn't contain any additional info");
        }
        final int taskId = info.stream().filter(p -> "externalTaskId".equals(p.getParameterName()))
                .mapToInt(value -> Integer.parseInt(value.getParameterValue())).findFirst().orElse(0);
        if (taskId == 0) {
            throw new IllegalArgumentException("Node doesn't contain mapped external id");
        }
        List<NameValuePair> params = new ArrayList<NameValuePair>() {{
            add(new BasicNameValuePair("taskId", String.valueOf(taskId)));
        }};
        submit(params);
    }

    @Override
    public void onUpdated(ProcessingExecutionTask task) {
        if (this.descriptor == null) {
            throw new RuntimeException(("External listener descriptor not set"));
        }

    }

    @Override
    public void onCompleted(ProcessingExecutionTask task, String processOutput) {
        if (this.descriptor == null) {
            throw new RuntimeException(("External listener descriptor not set"));
        }
        final List<ParameterValue> info = nodeProvider.get(task.getWorkflowNodeId()).getAdditionalInfo();
        if (info == null) {
            throw new IllegalArgumentException("Node doesn't contain any additional info");
        }
        final int taskId = info.stream().filter(p -> "externalTaskId".equals(p.getParameterName()))
                .mapToInt(value -> Integer.parseInt(value.getParameterValue())).findFirst().orElse(0);
        if (taskId == 0) {
            throw new IllegalArgumentException("Node doesn't contain mapped external id");
        }
        final LocalDateTime startTime = task.getStartTime();
        Duration duration = Duration.between(startTime, task.getLastUpdated());
        List<NameValuePair> params = new ArrayList<NameValuePair>() {{
            add(new BasicNameValuePair("taskId", String.valueOf(taskId)));
            add(new BasicNameValuePair("elapsed", String.valueOf(duration.toMillis())));
            add(new BasicNameValuePair("code", "0"));
            add(new BasicNameValuePair("output", processOutput));
        }};
        submit(params);
    }

    @Override
    public void onError(ProcessingExecutionTask task, String reason, int errorCode, String processOutput) {
        if (this.descriptor == null) {
            throw new RuntimeException(("External listener descriptor not set"));
        }
        final List<ParameterValue> info = nodeProvider.get(task.getWorkflowNodeId()).getAdditionalInfo();
        if (info == null) {
            throw new IllegalArgumentException("Node doesn't contain any additional info");
        }
        final int taskId = info.stream().filter(p -> "externalTaskId".equals(p.getParameterName()))
                .mapToInt(value -> Integer.parseInt(value.getParameterValue())).findFirst().orElse(0);
        if (taskId == 0) {
            throw new IllegalArgumentException("Node doesn't contain mapped external id");
        }
        final LocalDateTime startTime = task.getStartTime();
        Duration duration = Duration.between(startTime, task.getLastUpdated());
        List<NameValuePair> params = new ArrayList<NameValuePair>() {{
            add(new BasicNameValuePair("taskId", String.valueOf(taskId)));
            add(new BasicNameValuePair("elapsed", String.valueOf(duration.toMillis())));
            add(new BasicNameValuePair("code", String.valueOf(errorCode)));
            add(new BasicNameValuePair("reason", reason));
            add(new BasicNameValuePair("output", processOutput));
        }};
        submit(params);
    }

    private void submit(List<NameValuePair> params) {
        try (CloseableHttpResponse response = createConnection(params)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    break;
                case 401:
                    logger.severe("Unauthorized. Please check the sent credentials.");
                    break;
                default:
                    logger.severe(String.format("The request was not successful. Reason: %s",
                            response.getStatusLine().getReasonPhrase()));
                    break;
            }
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
    }

    private CloseableHttpResponse createConnection(List<NameValuePair> params) throws IOException {
        URL url = new URL("https", this.descriptor.getHostName(), this.descriptor.getPort(), this.descriptor.getEndpoint());
        return NetUtils.openConnection(HttpMethod.POST, url.toString(), this.descriptor.getCredentials(), params);
    }
}
