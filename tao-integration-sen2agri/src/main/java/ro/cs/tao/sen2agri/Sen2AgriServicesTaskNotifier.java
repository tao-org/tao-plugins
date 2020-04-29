package ro.cs.tao.sen2agri;

import org.apache.http.NameValuePair;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.message.BasicNameValuePair;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.execution.callback.CallbackClient;
import ro.cs.tao.execution.callback.CallbackClientFactory;
import ro.cs.tao.execution.callback.EndpointDescriptor;
import ro.cs.tao.execution.model.ProcessingExecutionTask;
import ro.cs.tao.execution.model.TaskListener;
import ro.cs.tao.persistence.WorkflowNodeProvider;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.utils.StringUtilities;
import ro.cs.tao.workflow.ParameterValue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Sen2AgriServicesTaskNotifier implements TaskListener {
    private final WorkflowNodeProvider nodeProvider;
    private EndpointDescriptor descriptor;
    private final Logger logger;

    public Sen2AgriServicesTaskNotifier() {
        logger = Logger.getLogger(Sen2AgriServicesTaskNotifier.class.getName());
        nodeProvider = SpringContextBridge.services().getService(WorkflowNodeProvider.class);
        final ConfigurationProvider cfgManager = ConfigurationManager.getInstance();
        final String protocol = cfgManager.getValue(Constants.SEN2AGRI_SERVICES_PROTOCOL);
        final String hostName = cfgManager.getValue(Constants.SEN2AGRI_SERVICES_HOSTNAME);
        final int port = Integer.parseInt(cfgManager.getValue(Constants.SEN2AGRI_SERVICES_PORT, "-1"));
        if (protocol != null && hostName != null && port > 0) {
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
                final String method = cfgManager.getValue(Constants.SEN2AGRI_SERVICES_METHOD);
                this.descriptor = new EndpointDescriptor(protocol.toLowerCase(), hostName, port, endpoint,
                                                         method.toUpperCase(), credentials);
                logger.fine(String.format("Sen2Agri-Services callback configured at https://%s:%d%s",
                                          hostName, port, endpoint));
            }
        } else {
            logger.warning("Sen2Agri-Services callback configuration not found");
        }
    }

    @Override
    public void setDescriptor(EndpointDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public EndpointDescriptor getDescriptor() { return this.descriptor; }

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
        final String stepName = info.stream().filter(p -> "externalStep".equals(p.getParameterName()))
                .map(ParameterValue::getParameterValue).findFirst().orElse(null);
        if (taskId == 0) {
            throw new IllegalArgumentException("Node doesn't contain mapped external id");
        }
        List<NameValuePair> params = new ArrayList<NameValuePair>() {{
            add(new BasicNameValuePair("host", task.getExecutionNodeHostName()));
            add(new BasicNameValuePair("taskId", String.valueOf(taskId)));
            add(new BasicNameValuePair("step", stepName));
            add(new BasicNameValuePair("status", task.getExecutionStatus().name()));
        }};
        call(params);
    }

    @Override
    public void onUpdated(ProcessingExecutionTask task) {
        if (this.descriptor == null) {
            throw new RuntimeException(("External listener descriptor not set"));
        }
        final List<ParameterValue> info = nodeProvider.get(task.getWorkflowNodeId()).getAdditionalInfo();
        if (info == null) {
            throw new IllegalArgumentException("Node doesn't contain any additional info");
        }
        final int taskId = info.stream().filter(p -> "externalTaskId".equals(p.getParameterName()))
                .mapToInt(value -> Integer.parseInt(value.getParameterValue())).findFirst().orElse(0);
        final String stepName = info.stream().filter(p -> "externalStep".equals(p.getParameterName()))
                .map(ParameterValue::getParameterValue).findFirst().orElse(null);
        if (taskId == 0) {
            throw new IllegalArgumentException("Node doesn't contain mapped external id");
        }
        final LocalDateTime startTime = task.getStartTime();
        Duration duration = Duration.between(startTime, task.getLastUpdated());
        List<NameValuePair> params = new ArrayList<NameValuePair>() {{
            add(new BasicNameValuePair("host", task.getExecutionNodeHostName()));
            add(new BasicNameValuePair("taskId", String.valueOf(taskId)));
            add(new BasicNameValuePair("step", stepName));
            add(new BasicNameValuePair("status", task.getExecutionStatus().name()));
            add(new BasicNameValuePair("elapsed", String.valueOf(duration.toMillis())));
        }};
        call(params);
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
        final String stepName = info.stream().filter(p -> "externalStep".equals(p.getParameterName()))
                .map(ParameterValue::getParameterValue).findFirst().orElse(null);
        if (taskId == 0) {
            throw new IllegalArgumentException("Node doesn't contain mapped external id");
        }
        final LocalDateTime startTime = task.getStartTime();
        Duration duration = Duration.between(startTime, task.getLastUpdated());
        List<NameValuePair> params = new ArrayList<NameValuePair>() {{
            add(new BasicNameValuePair("host", task.getExecutionNodeHostName()));
            add(new BasicNameValuePair("taskId", String.valueOf(taskId)));
            add(new BasicNameValuePair("step", stepName));
            add(new BasicNameValuePair("status", task.getExecutionStatus().name()));
            add(new BasicNameValuePair("elapsed", String.valueOf(duration.toMillis())));
            add(new BasicNameValuePair("code", "0"));
            add(new BasicNameValuePair("output", processOutput));
        }};
        call(params);
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
        final String stepName = info.stream().filter(p -> "externalStep".equals(p.getParameterName()))
                .map(ParameterValue::getParameterValue).findFirst().orElse(null);
        if (taskId == 0) {
            throw new IllegalArgumentException("Node doesn't contain mapped external id");
        }
        final LocalDateTime startTime = task.getStartTime();
        Duration duration = Duration.between(startTime, task.getLastUpdated());
        List<NameValuePair> params = new ArrayList<NameValuePair>() {{
            add(new BasicNameValuePair("host", task.getExecutionNodeHostName()));
            add(new BasicNameValuePair("taskId", String.valueOf(taskId)));
            add(new BasicNameValuePair("step", stepName));
            add(new BasicNameValuePair("status", task.getExecutionStatus().name()));
            add(new BasicNameValuePair("elapsed", String.valueOf(duration.toMillis())));
            add(new BasicNameValuePair("code", String.valueOf(errorCode)));
            add(new BasicNameValuePair("reason", reason));
            add(new BasicNameValuePair("output", processOutput));
        }};
        call(params);
    }

    private void call(List<NameValuePair> params) {
        final CallbackClient client = CallbackClientFactory.createFor(this.descriptor);
        final int retCode = client.call(params);
        switch (retCode) {
            case 200:
                logger.fine(String.format("Request to '%s' was successful", this.descriptor.toAnonString()));
                break;
            case 401:
                logger.severe(String.format("Unauthorized to call '%s'. Please check the sent credentials.",
                                            this.descriptor.toString()));
                break;
            default:
                logger.severe(String.format("The request to '%s' was not successful. Returned code: %d",
                                            this.descriptor.toAnonString(), retCode));
                break;
        }
    }
}
