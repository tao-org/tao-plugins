package ro.cs.tao.sen2agri;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.execution.model.ExternalTaskListener;
import ro.cs.tao.execution.model.ProcessingExecutionTask;
import ro.cs.tao.persistence.WorkflowNodeProvider;
import ro.cs.tao.sen2agri.model.TaskMessage;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.workflow.ParameterValue;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

public class Sen2AgriTaskNotifier implements ExternalTaskListener {
    private final String hostName;
    private final int port;
    private final boolean enabled;
    private final WorkflowNodeProvider nodeProvider;
    private final Logger logger;

    public Sen2AgriTaskNotifier() {
        final ConfigurationProvider cfgManager = ConfigurationManager.getInstance();
        hostName = cfgManager.getValue(Constants.HOSTNAME);
        port = Integer.parseInt(cfgManager.getValue(Constants.PORT, "8088"));
        enabled = hostName != null;
        logger = Logger.getLogger(Sen2AgriTaskNotifier.class.getName());
        nodeProvider = SpringContextBridge.services().getService(WorkflowNodeProvider.class);
        if (!enabled) {
            logger.severe("Sen2Agri configuration not found");
        }
    }

    @Override
    public void onStarted(ProcessingExecutionTask task) {
        if (enabled) {
            final List<ParameterValue> info = nodeProvider.get(task.getWorkflowNodeId()).getAdditionalInfo();
            if (info == null) {
                throw new IllegalArgumentException("Node doesn't contain any additional info");
            }
            final int taskId = info.stream().filter(p -> "externalTaskId".equals(p.getParameterName()))
                    .mapToInt(value -> Integer.parseInt(value.getParameterValue())).findFirst().orElse(0);
            if (taskId == 0) {
                throw new IllegalArgumentException("Node doesn't contain mapped external id");
            }
            try (Socket socket = createSocket()) {
                final TaskMessage message = TaskMessage.createStartMessage(taskId, task.getComponent().getId(), task.getInstanceId());
                socket.getOutputStream().write(message.toString().getBytes());
            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }
    }

    @Override
    public void onUpdated(ProcessingExecutionTask task) {
        if (enabled) {
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
            try (Socket socket = createSocket()) {
                final TaskMessage message = TaskMessage.createProgressMessage(taskId, task.getComponent().getId(), task.getInstanceId(), duration.toMillis());
                socket.getOutputStream().write(message.toString().getBytes());
            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }
    }

    @Override
    public void onCompleted(ProcessingExecutionTask task, String processOutput) {
        if (enabled) {
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
            try (Socket socket = createSocket()) {
                final TaskMessage message = TaskMessage.createEndMessage(taskId, task.getComponent().getId(),
                                                                         task.getInstanceId(), processOutput, 0,
                                                                         duration.toMillis());
                socket.getOutputStream().write(message.toString().getBytes());
            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }
    }

    @Override
    public void onError(ProcessingExecutionTask task, String reason, int errorCode, String processOutput) {
        if (enabled) {
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
            try (Socket socket = createSocket()) {
                final TaskMessage message = TaskMessage.createErrorMessage(taskId, task.getComponent().getId(),
                                                                           task.getInstanceId(), reason, processOutput,
                                                                           errorCode, duration.toMillis());
                socket.getOutputStream().write(message.toString().getBytes());
            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }
    }

    private Socket createSocket() throws IOException {
        return new Socket(hostName, port);
    }
}
