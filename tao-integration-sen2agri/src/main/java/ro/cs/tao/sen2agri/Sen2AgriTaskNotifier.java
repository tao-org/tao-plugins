package ro.cs.tao.sen2agri;

import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.configuration.ConfigurationProvider;
import ro.cs.tao.execution.ExecutionConfiguration;
import ro.cs.tao.execution.model.EndpointDescriptor;
import ro.cs.tao.execution.model.ProcessingExecutionTask;
import ro.cs.tao.execution.model.TaskListener;
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
        int port = Integer.parseInt(cfgManager.getValue(Constants.SEN2AGRI_LISTENER_PORT, devMode ? "9001" : "-1"));
        if (hostName == null || port == -1) {
            logger.warning("Sen2Agri callback configuration not found");
        }  else {
            this.descriptor = new EndpointDescriptor("tcp", hostName, port, null);
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

    @Override
    public void onCompleted(ProcessingExecutionTask task, String processOutput) {
        if (this.descriptor == null) {
            throw new RuntimeException(("External listener descriptor not set"));
        }
        final List<ParameterValue> info = nodeProvider.get(task.getWorkflowNodeId()).getAdditionalInfo();
        if (info != null) {
            final int taskId = info.stream().filter(p -> "externalTaskId".equals(p.getParameterName()))
                    .mapToInt(value -> Integer.parseInt(value.getParameterValue())).findFirst().orElse(0);
            if (taskId != 0) {
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

    private Socket createSocket() throws IOException {
        return new Socket(this.descriptor.getHostName(), this.descriptor.getPort());
    }
}
