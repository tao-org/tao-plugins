package ro.cs.tao.execution.ogc;

import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.component.Variable;
import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.component.ogc.OGCProcessComponent;
import ro.cs.tao.docker.Container;
import ro.cs.tao.execution.ExecutionException;
import ro.cs.tao.execution.Executor;
import ro.cs.tao.execution.model.ExecutionJob;
import ro.cs.tao.execution.model.ExecutionStatus;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.model.OGCProcessExecutionTask;
import ro.cs.tao.execution.ogc.model.ExecuteRequest;
import ro.cs.tao.execution.persistence.ExecutionJobProvider;
import ro.cs.tao.execution.persistence.ExecutionTaskProvider;
import ro.cs.tao.persistence.PersistenceException;
import ro.cs.tao.persistence.WebServiceAuthenticationProvider;
import ro.cs.tao.utils.ExceptionUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class OGCProcessExecutor extends Executor<OGCProcessExecutionTask> {

    public static final String URN_OGC_DEF_ANY_URI = "urn:ogc:def:anyURI";
    //private WPSComponent wpsComponent;
    private static ExecutionTaskProvider taskProvider;
    private static ExecutionJobProvider jobProvider;
    private static WebServiceAuthenticationProvider authenticationProvider;
    //private final Map<String, WebProcessingService> webServicesMap;
    private final ExecutorService backgroundWorker;
    private final Logger logger = Logger.getLogger(OGCProcessExecutor.class.getName());

    public static void setTaskProvider(ExecutionTaskProvider provider) {
        OGCProcessExecutor.taskProvider = provider;
    }

    public static void setJobProvider(ExecutionJobProvider jobProvider) {
        OGCProcessExecutor.jobProvider = jobProvider;
    }

    public static void setAuthenticationProvider(WebServiceAuthenticationProvider authenticationProvider) {
        OGCProcessExecutor.authenticationProvider = authenticationProvider;
    }

    public OGCProcessExecutor() {
        //this.webServicesMap = new HashMap<>();
        this.backgroundWorker = Executors.newSingleThreadExecutor();
    }

    @Override
    public boolean supports(TaoComponent component) { return component instanceof OGCProcessComponent; }

    @Override
    public void initialize() throws ExecutionException {
        super.initialize(60000);
    }

    @Override
    public void execute(OGCProcessExecutionTask task) throws ExecutionException {
        try {
            final OGCProcessClient client = getClient(task);
            List<Variable> values = task.getInputParameterValues();
            if (values == null || values.isEmpty()) {
                throw new ExecutionException("No input data for the task");
            }
            final OGCProcessComponent ogcComponent = task.getComponent();
            // Execute a process synchronously
            ExecuteRequest executeRequest = new ExecuteRequest();
            final Map<String, Object> inputs = new HashMap<>();
            try {
                for (Variable v : values) {
                    inputs.put(v.getKey().contains("~")
                                      ? v.getKey()
                                      : task.getComponent().getId() + "~" + v.getKey(),
                              v.getValue());
                }
                //input.putAll(JacksonUtil.fromString(values.get(0).getValue(), input.getClass()));
            } catch (IllegalArgumentException ignored) {
            }
            executeRequest.setInputs(inputs);
            executeRequest.setMode("sync");
            this.backgroundWorker.submit(() -> {
                try {
                    task.setStartTime(LocalDateTime.now());
                    client.executeProcess(ogcComponent.getId(), executeRequest)
                          .thenAccept(result -> {
                              Map<String, Object> outputs = result.getOutputs();
                              if (!"accepted".equalsIgnoreCase(outputs.get("status").toString())) {
                                  task.setEndTime(LocalDateTime.now());
                                  changeTaskStatus(task, ExecutionStatus.FAILED,
                                                   "OGC Process task failed: " + outputs.get("status"));
                              } else {
                                  if (outputs.containsKey("jobId")) {
                                      task.setResourceId(outputs.get("jobId").toString());
                                  } else {
                                      task.setResourceId(UUID.randomUUID().toString());
                                  }
                                  logger.fine(String.format("Successfully submitted task with resource id %s", task.getResourceId()));
                                  changeTaskStatus(task, ExecutionStatus.RUNNING, null);
                              }
                          })
                          .exceptionally(throwable -> {
                              throw new ExecutionException("OGC Process task threw an exception: " + throwable.getMessage(), throwable);
                          });
                } catch (Exception inner) {
                    onException(task, inner);
                }
            });
        } catch (Exception ex) {
            onException(task, ex);
        }
    }

    @Override
    public void stop(OGCProcessExecutionTask task) throws ExecutionException {
        try {
            final OGCProcessClient client = getClient(task);
            client.cancelJob(task.getResourceId());
        } catch (MalformedURLException e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public void suspend(OGCProcessExecutionTask task) throws ExecutionException {
        throw new ExecutionException("suspend() not supported on OGC Process");
    }

    @Override
    public void resume(OGCProcessExecutionTask task) throws ExecutionException {
        throw new ExecutionException("resume() not supported on OGC Process");
    }

    @Override
    public void monitorExecutions() throws ExecutionException {
        if(!isInitialized.get()) {
            return;
        }
        List<ExecutionTask> tasks = taskProvider.listWPSExecuting();
        if (tasks != null) {
            for (ExecutionTask task : tasks) {
                try {
                    final OGCProcessExecutionTask ogcTask = (OGCProcessExecutionTask) task;
                    final OGCProcessClient client = getClient(ogcTask);
                    final String jobId = ogcTask.getResourceId();
                    client.getJobStatus(jobId)
                          .thenCompose(status -> {
                              try {
                                  taskProvider.updateStatus(task, ExecutionStatus.RUNNING,
                                                            String.format("Task %s is at %d%%",
                                                                          task.getId(), status.getProgress()));
                              } catch (PersistenceException e) {
                                  logger.severe(ExceptionUtils.getStackTrace(logger, e));
                                  return null;
                              }
                              if ("successful".equals(status.getStatus())) {
                                  markTaskFinished(task, ExecutionStatus.DONE, null);
                              } else if ("failed".equals(status.getStatus())) {
                                  markTaskFinished(task, ExecutionStatus.FAILED,
                                                   status.getStatus() + ": " + status.getMessage());
                              }
                              return null;
                          });
                } catch (Exception ex) {
                    logger.severe(String.format("%s: Error while getting the status for the task %s [%s]",
                                                ex.getClass().getName(), task.getId(), ex.getMessage()));
                    markTaskFinished(task, ExecutionStatus.FAILED, ex.getMessage());
                }
            }
        }
    }

    private OGCProcessClient getClient(OGCProcessExecutionTask task) throws MalformedURLException{
        final OGCProcessComponent ogcComponent = task.getComponent();
        final Container service = ogcComponent.getService();
        final WebServiceAuthentication authentication = authenticationProvider.get(service.getId());
        final String address = ogcComponent.getRemoteAddress();
        final URL url = new URL(address);
        task.setExecutionNodeHostName(url.getHost());
        return new OGCProcessClient(ogcComponent.getRemoteAddress(), authentication);
    }

    private void onException(OGCProcessExecutionTask task, Exception ex) {
        logger.severe(String.format("Task %s FAILED to execute. Reason: %s", task.getId(), ex.getMessage()));
        markTaskFinished(task, ExecutionStatus.FAILED, ex.getMessage());
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
