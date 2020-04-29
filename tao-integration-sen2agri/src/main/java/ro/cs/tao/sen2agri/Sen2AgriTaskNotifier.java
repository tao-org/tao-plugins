package ro.cs.tao.sen2agri;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.execution.ExecutionConfiguration;
import ro.cs.tao.execution.callback.CallbackClient;
import ro.cs.tao.execution.callback.CallbackClientFactory;
import ro.cs.tao.execution.callback.EndpointDescriptor;
import ro.cs.tao.execution.callback.ResponseConverter;
import ro.cs.tao.execution.model.ProcessingExecutionTask;
import ro.cs.tao.execution.model.TaskListener;
import ro.cs.tao.persistence.WorkflowNodeProvider;
import ro.cs.tao.sen2agri.model.TaskMessage;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.workflow.ParameterValue;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Sen2AgriTaskNotifier implements TaskListener {
    private EndpointDescriptor descriptor;
    private final WorkflowNodeProvider nodeProvider;
    private final Logger logger;

    public Sen2AgriTaskNotifier() {
        logger = Logger.getLogger(Sen2AgriTaskNotifier.class.getName());
        nodeProvider = SpringContextBridge.services().getService(WorkflowNodeProvider.class);
        final ConfigurationProvider cfgManager = ConfigurationManager.getInstance();
        final boolean devMode = ExecutionConfiguration.developmentModeEnabled();
        String hostName = cfgManager.getValue(Constants.SEN2AGRI_HOSTNAME, devMode ? "127.0.0.1" : null);
        String proto = cfgManager.getValue(Constants.SEN2AGRI_LISTENER_PROTO);
        int port = Integer.parseInt(cfgManager.getValue(Constants.SEN2AGRI_LISTENER_PORT, devMode ? "9001" : "-1"));
        if (hostName == null || proto == null || port == -1) {
            logger.warning("Sen2Agri callback configuration not found");
        }  else {
            this.descriptor = new EndpointDescriptor(proto, hostName, port, null);
            logger.fine(String.format("Sen2Agri callback configured at %s:%d", hostName, port));
            if (devMode) {
                try {
                    new Sen2AgriMockListener(port).start();
                    logger.fine(String.format("Started Sen2Agri callback listener on port %d", port));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void setDescriptor(EndpointDescriptor descriptor) { this.descriptor = descriptor; }

    @Override
    public EndpointDescriptor getDescriptor() { return this.descriptor; }

    @Override
    public void onStarted(ProcessingExecutionTask task) {
        if (this.descriptor == null) {
            throw new RuntimeException(("External listener descriptor not set"));
        }
        final List<NameValuePair> params = prepareParams(task, "STARTED", null, 0, null, null);
        call(params);
    }

    @Override
    public void onUpdated(ProcessingExecutionTask task) {
        if (this.descriptor == null) {
            throw new RuntimeException(("External listener descriptor not set"));
        }
        final List<NameValuePair> params = prepareParams(task, "STARTED", null, 0, null, null);
        call(params);
    }

    @Override
    public void onCompleted(ProcessingExecutionTask task, String processOutput) {
        if (this.descriptor == null) {
            throw new RuntimeException(("External listener descriptor not set"));
        }
        final List<NameValuePair> params = prepareParams(task, "ENDED", "OK", 0, processOutput, null);
        call(params);
    }

    @Override
    public void onError(ProcessingExecutionTask task, String reason, int errorCode, String processOutput) {
        if (this.descriptor == null) {
            throw new RuntimeException(("External listener descriptor not set"));
        }
        final List<NameValuePair> params = prepareParams(task, "ENDED", "FAILED", errorCode, processOutput, reason);
        call(params);
    }

    private List<NameValuePair> prepareParams(ProcessingExecutionTask task, String messageType, String status, int exitCode, String output, String error) {
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
        Duration duration = Duration.between(startTime, task.getEndTime());
        return new ArrayList<NameValuePair>() {{
            add(new BasicNameValuePair("host", task.getExecutionNodeHostName()));
            add(new BasicNameValuePair("messageType", messageType));
            add(new BasicNameValuePair("taskId", String.valueOf(taskId)));
            add(new BasicNameValuePair("step", task.getComponent().getId()));
            add(new BasicNameValuePair("stepIndex", String.valueOf(task.getInstanceId())));
            add(new BasicNameValuePair("status", status));
            add(new BasicNameValuePair("elapsed", String.valueOf(duration.toMillis())));
            add(new BasicNameValuePair("code", String.valueOf(exitCode)));
            add(new BasicNameValuePair("reason", error));
            add(new BasicNameValuePair("output", output));
        }};
    }

    private void call(List<NameValuePair> params) {
        final CallbackClient client = CallbackClientFactory.createFor(this.descriptor);
        client.setConverter(new Sen2AgriResponseConverter());
        final int retCode = client.call(params);
        if (retCode == 0) {
            logger.severe(String.format("No message was sent to remote endpoint '%s'. Please check the configuration.",
                    this.descriptor.toAnonString()));
        } else {
            logger.fine(String.format("Sent %d bytes to remote endpoint '%s'", retCode, this.descriptor.toAnonString()));
        }
    }

    private static class Sen2AgriResponseConverter implements ResponseConverter {

        @Override
        public String convert(List<NameValuePair> parameters) {
            final TaskMessage message = new TaskMessage();
            for (NameValuePair pair : parameters) {
                switch (pair.getName()) {
                    case "messageType":
                        message.setMessageType(pair.getValue());
                        break;
                    case "taskId":
                        message.setExternalTaskId(Integer.parseInt(pair.getValue()));
                        break;
                    case "status":
                        message.setStatus(pair.getValue());
                        break;
                    case "step":
                        message.setStepName(pair.getValue());
                        break;
                    case "stepIndex":
                        message.setStepIndex(Integer.parseInt(pair.getValue()));
                        break;
                    case "code":
                        message.setExitCode(Integer.parseInt(pair.getValue()));
                        break;
                    case "output":
                        message.setStdOutText(pair.getValue());
                        break;
                    case "reason":
                        message.setStdErrText(pair.getValue());
                        break;
                    case "elapsed":
                        message.setExecTime(Long.parseLong(pair.getValue()));
                        break;
                    default:
                        break;
                }
            }
            return message.toString();
        }
    }
}
