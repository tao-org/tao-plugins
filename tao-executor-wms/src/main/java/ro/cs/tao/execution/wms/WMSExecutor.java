package ro.cs.tao.execution.wms;

import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.component.Variable;
import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.component.ogc.WMSComponent;
import ro.cs.tao.docker.Container;
import ro.cs.tao.execution.ExecutionException;
import ro.cs.tao.execution.Executor;
import ro.cs.tao.execution.model.ExecutionJob;
import ro.cs.tao.execution.model.ExecutionStatus;
import ro.cs.tao.execution.model.ExecutionTask;
import ro.cs.tao.execution.model.WMSExecutionTask;
import ro.cs.tao.execution.persistence.ExecutionJobProvider;
import ro.cs.tao.persistence.PersistenceException;
import ro.cs.tao.persistence.WebServiceAuthenticationProvider;
import ro.cs.tao.security.UserPrincipal;
import ro.cs.tao.utils.ExceptionUtils;
import ro.cs.tao.wms.impl.WMSClient;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class WMSExecutor extends Executor<WMSExecutionTask> {

    private static ExecutionJobProvider jobProvider;
    private static WebServiceAuthenticationProvider authenticationProvider;

    private final ExecutorService backgroundWorker;
    private final Logger logger = Logger.getLogger(WMSExecutor.class.getName());

    public static void setJobProvider(ExecutionJobProvider jobProvider) {
        WMSExecutor.jobProvider = jobProvider;
    }

    public static void setAuthenticationProvider(WebServiceAuthenticationProvider authenticationProvider) {
        WMSExecutor.authenticationProvider = authenticationProvider;
    }

    public WMSExecutor() {
        this.backgroundWorker = Executors.newFixedThreadPool(4);
    }

    @Override
    public void initialize() throws ExecutionException {
        isInitialized.set(true);
    }

    @Override
    public boolean supports(TaoComponent component) { return component instanceof WMSComponent; }

    @Override
    public void execute(WMSExecutionTask task) throws ExecutionException {
        this.backgroundWorker.execute(() -> {
            try {
                final WMSComponent wmsComponent = task.getComponent();
                final Container service = wmsComponent.getService();
                final WebServiceAuthentication authentication = authenticationProvider.get(service.getId());
                final String address = wmsComponent.getRemoteAddress();
                final URL url = new URL(address);
                task.setExecutionNodeHostName(url.getHost());
                List<Variable> values = task.getInputParameterValues();
                if (values == null || values.isEmpty()) {
                    throw new ExecutionException("No input data for the task");
                }
                final String userId = task.getJob().getUserId();
                final Path path = Paths.get(task.buildOutputPath());
                final WMSClient client = new WMSClient(address, authentication, new UserPrincipal(userId));
                client.getMap(wmsComponent.getCapabilityName(), values.stream().collect(Collectors.toMap(Variable::getKey, Variable::getValue)), path);
                onSuccess(task);
            } catch (Exception e) {
                onException(task, e);
            }
        });
    }

    @Override
    public void stop(WMSExecutionTask task) throws ExecutionException {

    }

    @Override
    public void suspend(WMSExecutionTask task) throws ExecutionException {
        throw new ExecutionException("suspend() not supported on WMS");
    }

    @Override
    public void resume(WMSExecutionTask task) throws ExecutionException {
        throw new ExecutionException("resume() not supported on WMS");
    }

    @Override
    public void monitorExecutions() throws ExecutionException {
        // NO-OP The WMS calls are synchronous and end with a callback
    }

    @Override
    public String defaultId() { return "WMSExecutor"; }

    private void onSuccess(WMSExecutionTask task) {
        markTaskFinished(task, ExecutionStatus.DONE);
    }

    private void onException(WMSExecutionTask task, Exception ex) {
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
