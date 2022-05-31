package ro.cs.tao.execution.wps;

import net.opengis.ows11.ExceptionReportType;
import net.opengis.wps10.*;
import org.geotools.data.wps.WPSFactory;
import org.geotools.data.wps.WebProcessingService;
import org.geotools.data.wps.request.DescribeProcessRequest;
import org.geotools.data.wps.response.DescribeProcessResponse;
import org.geotools.data.wps.response.ExecuteProcessResponse;
import org.geotools.process.Process;
import ro.cs.tao.component.*;
import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.docker.Container;
import ro.cs.tao.execution.ExecutionException;
import ro.cs.tao.execution.Executor;
import ro.cs.tao.execution.model.ExecutionJob;
import ro.cs.tao.execution.model.ExecutionStatus;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.model.WPSExecutionTask;
import ro.cs.tao.execution.persistence.ExecutionJobProvider;
import ro.cs.tao.execution.persistence.ExecutionTaskProvider;
import ro.cs.tao.persistence.PersistenceException;
import ro.cs.tao.persistence.WPSAuthenticationProvider;
import ro.cs.tao.utils.ExceptionUtils;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WPSExecutor extends Executor<WPSExecutionTask> {

    public static final String URN_OGC_DEF_ANY_URI = "urn:ogc:def:anyURI";
    //private WPSComponent wpsComponent;
    private static ExecutionTaskProvider taskProvider;
    private static ExecutionJobProvider jobProvider;
    private static WPSAuthenticationProvider authenticationProvider;
    private final Map<String, WebProcessingService> webServicesMap;
    private final ExecutorService backgroundWorker;

    public static void setTaskProvider(ExecutionTaskProvider provider) {
        WPSExecutor.taskProvider = provider;
    }

    public static void setJobProvider(ExecutionJobProvider jobProvider) {
        WPSExecutor.jobProvider = jobProvider;
    }

    public static void setAuthenticationProvider(WPSAuthenticationProvider authenticationProvider) {
        WPSExecutor.authenticationProvider = authenticationProvider;
    }

    public WPSExecutor() {
        this.webServicesMap = new HashMap<>();
        this.backgroundWorker = Executors.newSingleThreadExecutor();
    }

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
            logger.fine(String.format("Successfully submitted task with id %s", task.getResourceId()));
            final WPSComponent wpsComponent = task.getComponent();
            final Container service = wpsComponent.getService();
            final WPSAuthentication authentication = authenticationProvider.get(service.getId());
            final String address = wpsComponent.getRemoteAddress();
            final URL url = new URL(address);
            task.setExecutionNodeHostName(url.getHost());
            task.setStartTime(LocalDateTime.now());
            changeTaskStatus(task, ExecutionStatus.RUNNING);
            List<Variable> values = task.getInputParameterValues();
            if (values == null || values.size() == 0) {
                throw new ExecutionException("No input data for the task");
            }
            final WebProcessingService wps = this.webServicesMap.computeIfAbsent(address,
                                                 addr -> {
                                                    try {
                                                        return new WebProcessingService(url,
                                                                                        new WPSHttpClient(authentication),
                                                                                        null);
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                    return null;
                                                });
            if (wps == null) {
                throw new ExecutionException(String.format("Cannot create WPS client for address %s", address));
            }
            final DescribeProcessRequest descRequest = wps.createDescribeProcessRequest();
            descRequest.setIdentifier(wpsComponent.getCapabilityName());
            final DescribeProcessResponse descResponse = wps.issueRequest(descRequest);
            final ProcessDescriptionsType processDesc = descResponse.getProcessDesc();
            final ProcessDescriptionType pdt = (ProcessDescriptionType) processDesc.getProcessDescription().get(0);
            for (Object dataInput : pdt.getDataInputs().getInput()) {
                InputDescriptionType dataInputTmp = (InputDescriptionType) dataInput;
                if (dataInputTmp.getLiteralData().getDataType().getReference() == null) {
                    final ParameterDescriptor parameter = wpsComponent.getParameters().stream().filter(p -> p.getName().equals(dataInputTmp.getIdentifier().getValue())).findFirst().orElse(null);
                    String taoType = URN_OGC_DEF_ANY_URI;
                    if (parameter != null) {
                        taoType = JavaType.fromClass(parameter.getDataType()).ogcURN();
                    }
                    dataInputTmp.getLiteralData().getDataType().setReference(taoType);
                }
            }
            final WPSFactory wpsfactory = new WPSFactory(pdt, url);
            final Process process = wpsfactory.create();
            final Map<String, Object> input = new HashMap<>();
            try {
                for (Variable v : values) {
                    input.put(wpsComponent.getId() + "~" + v.getKey(), v.getValue());
                }
                //input.putAll(JacksonUtil.fromString(values.get(0).getValue(), input.getClass()));
            } catch (IllegalArgumentException ignored) {
            }
            this.backgroundWorker.submit(() -> {
                try {
                    process.execute(input, null);
                } catch (Exception inner) {
                    onException(task, inner);
                }
            });
        } catch (Exception ex) {
            onException(task, ex);
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
        List<ExecutionTask> tasks = taskProvider.listRemoteExecuting();
        if (tasks != null) {
            for (ExecutionTask task : tasks) {
                try {
                    final WPSExecutionTask wpsTask = (WPSExecutionTask) task;
                    final WPSComponent wpsComponent = wpsTask.getComponent();
                    final Container service = wpsComponent.getService();
                    final WPSAuthentication authentication = authenticationProvider.get(service.getId());
                    WebProcessingService wps = new WebProcessingService(new URL(wpsComponent.getRemoteAddress()),
                                                                        new WPSHttpClient(authentication), null);
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
                            taskProvider.updateStatus(task, ExecutionStatus.RUNNING);
                        } else if (status.getProcessAccepted() != null) {
                            taskProvider.updateStatus(task, ExecutionStatus.RUNNING);
                        } else if (status.getProcessPaused() != null) {
                            logger.fine(String.format("Task %s was paused at %d%%",
                                                      task.getId(), status.getProcessPaused().getPercentCompleted().intValue()));
                            taskProvider.updateStatus(task, ExecutionStatus.SUSPENDED);
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

    private void onException(WPSExecutionTask task, Exception ex) {
        logger.severe(String.format("Task %s FAILED to execute. Reason: %s", task.getId(), ex.getMessage()));
        markTaskFinished(task, ExecutionStatus.FAILED);
        final ExecutionJob job = task.getJob();
        final List<ExecutionTask> tasks = job.getTasks();
        if (tasks.size() == 1) {
            job.setExecutionStatus(ExecutionStatus.FAILED);
            try {
                jobProvider.update(job);
            } catch (PersistenceException e) {
                logger.severe(ExceptionUtils.getStackTrace(logger, e));
            }
        }
    }
}
