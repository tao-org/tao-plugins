package ro.cs.tao.sen2agri;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import ro.cs.tao.execution.callback.CallbackClient;
import ro.cs.tao.execution.callback.CallbackClientFactory;
import ro.cs.tao.execution.callback.EndpointDescriptor;
import ro.cs.tao.execution.model.AbstractTaskListener;
import ro.cs.tao.execution.model.ProcessingExecutionTask;
import ro.cs.tao.persistence.WorkflowNodeProvider;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.workflow.ParameterValue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Sen2AgriServicesTaskNotifier extends AbstractTaskListener {
    private final WorkflowNodeProvider nodeProvider;

    public Sen2AgriServicesTaskNotifier() {
        super();
        nodeProvider = SpringContextBridge.services().getService(WorkflowNodeProvider.class);
    }

    @Override
    public boolean supportsProtocol(String protocol) {
        return protocol != null &&
                (protocol.toLowerCase().equals("http") || protocol.toLowerCase().equals("https"));
    }

    @Override
    public void onStarted(ProcessingExecutionTask task) {
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
        call(getCallbackFor(task), params);
    }

    @Override
    public void onUpdated(ProcessingExecutionTask task) {
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
        call(getCallbackFor(task), params);
    }

    @Override
    public void onCompleted(ProcessingExecutionTask task, String processOutput) {
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
        call(getCallbackFor(task), params);
    }

    @Override
    public void onError(ProcessingExecutionTask task, String reason, int errorCode, String processOutput) {
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
        call(getCallbackFor(task), params);
    }

    private void call(EndpointDescriptor descriptor, List<NameValuePair> params) {
        final CallbackClient client = CallbackClientFactory.createFor(descriptor);
        final int retCode = client.call(params);
        switch (retCode) {
            case 200:
                logger.fine(String.format("Request to '%s' was successful", descriptor.toAnonString()));
                break;
            case 401:
                logger.severe(String.format("Unauthorized to call '%s'. Please check the sent credentials.",
                                            descriptor.toString()));
                break;
            default:
                logger.severe(String.format("The request to '%s' was not successful. Returned code: %d",
                                            descriptor.toAnonString(), retCode));
                break;
        }
    }
}
