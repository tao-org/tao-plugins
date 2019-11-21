package ro.cs.tao.execution.wps;

import net.opengis.ows11.ExceptionReportType;
import net.opengis.wps10.*;
import org.geotools.data.wps.WPSFactory;
import org.geotools.data.wps.WebProcessingService;
import org.geotools.data.wps.request.DescribeProcessRequest;
import org.geotools.data.wps.response.DescribeProcessResponse;
import org.geotools.data.wps.response.ExecuteProcessResponse;
import org.geotools.process.Process;
import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.component.Variable;
import ro.cs.tao.component.WPSComponent;
import ro.cs.tao.execution.ExecutionException;
import ro.cs.tao.execution.Executor;
import ro.cs.tao.execution.model.ExecutionStatus;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.model.WPSExecutionTask;
import ro.cs.tao.persistence.data.jsonutil.JacksonUtil;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WPSExecutor extends Executor<WPSExecutionTask> {

    private WPSComponent wpsComponent;

    @Override
    public boolean supports(TaoComponent component) { return component instanceof WPSComponent; }

    @Override
    public void initialize() throws ExecutionException {
        super.initialize(60000);
    }

    @Override
    public void execute(WPSExecutionTask task) throws ExecutionException {
        try {
            task.setResourceId(UUID.randomUUID().toString());
            logger.fine(String.format("Succesfully submitted task with id %s", task.getResourceId()));
            wpsComponent = task.getWpsComponent();
            task.setExecutionNodeHostName(wpsComponent.getRemoteAddress());
            task.setStartTime(LocalDateTime.now());
            changeTaskStatus(task, ExecutionStatus.RUNNING);
            List<Variable> values = task.getInputParameterValues();
            if (values == null || values.size() == 0) {
                throw new ExecutionException("No input data for the task");
            }
            Map<String, Object> input = new HashMap<>();
            try {
                input = JacksonUtil.fromString(values.get(0).getValue(), input.getClass());
            } catch (IllegalArgumentException iex) {
                input = null;
            }
            if (input == null) {
                throw new ExecutionException("Invalid input data for the task");
            }
            URL url = new URL(wpsComponent.getRemoteAddress());
            WebProcessingService wps = new WebProcessingService(url);
            DescribeProcessRequest descRequest = wps.createDescribeProcessRequest();
            descRequest.setIdentifier(wpsComponent.getCapabilityName());
            DescribeProcessResponse descResponse = wps.issueRequest(descRequest);
            ProcessDescriptionsType processDesc = descResponse.getProcessDesc();
            ProcessDescriptionType pdt = (ProcessDescriptionType) processDesc.getProcessDescription().get(0);
            WPSFactory wpsfactory = new WPSFactory(pdt, url);
            Process process = wpsfactory.create();
            process.execute(input, null);
        } catch (Exception ex) {
            logger.severe(ex.getMessage());
            markTaskFinished(task, ExecutionStatus.FAILED);
        }
    }

    @Override
    public void stop(WPSExecutionTask task) throws ExecutionException {

    }

    @Override
    public void suspend(WPSExecutionTask task) throws ExecutionException {
        throw new ExecutionException("suspend() not supported on WPS");
    }

    @Override
    public void resume(WPSExecutionTask task) throws ExecutionException {
        throw new ExecutionException("resume() not supported on WPS");
    }

    @Override
    public void monitorExecutions() throws ExecutionException {
        if(!isInitialized) {
            return;
        }
        List<ExecutionTask> tasks = persistenceManager.getRemoteExecutingTasks();
        if (tasks != null) {
            for (ExecutionTask task : tasks) {
                try {
                    WebProcessingService wps = new WebProcessingService(new URL(wpsComponent.getRemoteAddress()));
                    final ExecuteProcessResponse response = wps.issueStatusRequest(new URL(wpsComponent.getRemoteAddress() + "?service=WPS&request=GetStatus"));
                    // Checking for Exceptions and Status...
                    final ExceptionReportType exceptionResponse = response.getExceptionResponse();
                    final ExecuteResponseType executeResponse = response.getExecuteResponse();
                    if ((exceptionResponse == null) && (executeResponse != null)) {
                        final StatusType status = executeResponse.getStatus();
                        if (status.getProcessSucceeded() != null) {
                            // Process complete ... checking output
                            for (Object processOutput : executeResponse.getProcessOutputs().getOutput()) {
                                OutputDataType wpsOutput = (OutputDataType) processOutput;
                                // retrieve the value of the output ...
                                task.setOutputParameterValue(wpsOutput.getIdentifier().getValue(),
                                                             wpsOutput.getData().getLiteralData().getValue());
                            }
                            markTaskFinished(task, ExecutionStatus.DONE);
                        } else if (status.getProcessFailed() != null) {
                            // Process failed ... handle failed status
                            markTaskFinished(task, ExecutionStatus.FAILED);
                        } else if (status.getProcessStarted() != null) {
                            logger.fine(String.format("Task %s is at %d%%",
                                                      task.getId(), status.getProcessStarted().getPercentCompleted().intValue()));
                            persistenceManager.updateTaskStatus(task, ExecutionStatus.RUNNING);
                        } else if (status.getProcessAccepted() != null) {
                            persistenceManager.updateTaskStatus(task, ExecutionStatus.RUNNING);
                        } else if (status.getProcessPaused() != null) {
                            logger.fine(String.format("Task %s was paused at %d%%",
                                                      task.getId(), status.getProcessPaused().getPercentCompleted().intValue()));
                            persistenceManager.updateTaskStatus(task, ExecutionStatus.SUSPENDED);
                        } else {
                            throw new Exception("Empty status");
                        }
                    } else if (exceptionResponse != null) {
                        // Retrieve here the Exception message and handle the errored status ...
                        StringBuilder builder = new StringBuilder();
                        for (Object o : exceptionResponse.getException()) {
                            builder.append(o).append("\n");
                        }
                        logger.warning(String.format("Task %s FAILED. Process output: %s", task.getId(), builder.toString()));
                        markTaskFinished(task, ExecutionStatus.FAILED);
                    } else {
                        throw new Exception("Empty response");
                    }
                } catch (Exception ex) {
                    logger.severe(String.format("%s: Error while getting the status for the task %s [%s]",
                                                ex.getClass().getName(), task.getId(), ex.getMessage()));
                    markTaskFinished(task, ExecutionStatus.FAILED);
                }
            }
        }
    }

    @Override
    public String defaultId() { return "WPSExecutor"; }

}
