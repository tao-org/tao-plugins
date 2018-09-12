package ro.cs.tao.execution.wps;

import net.opengis.wps10.ProcessDescriptionType;
import net.opengis.wps10.ProcessDescriptionsType;
import org.geotools.data.wps.WPSFactory;
import org.geotools.data.wps.WebProcessingService;
import org.geotools.data.wps.request.DescribeProcessRequest;
import org.geotools.data.wps.response.DescribeProcessResponse;
import org.geotools.process.Process;
import ro.cs.tao.component.TaoComponent;
import ro.cs.tao.component.Variable;
import ro.cs.tao.component.WPSComponent;
import ro.cs.tao.execution.ExecutionException;
import ro.cs.tao.execution.Executor;
import ro.cs.tao.execution.model.ExecutionStatus;
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
        //TODO: Not yet implemented in GeoTools-WPS
        /*WebProcessingService wps = new WebProcessingService(new URL(wpsComponent.getRemoteAddress()));
        GetExecutionStatusRequest execRequest = wps.createGetExecutionStatusRequest();
        execRequest.setIdentifier(this.wpsProcessIdentifier);

        GetExecutionStatusResponse response = wps.issueRequest(execRequest);

        // Checking for Exceptions and Status...
        if ((response.getExceptionResponse() == null) && (response.getExecuteResponse() != null)) {
            if (response.getExecuteResponse().getStatus().getProcessSucceeded() != null) {
                // Process complete ... checking output
                for (Object processOutput : response.getExecuteResponse().getProcessOutputs().getOutput()) {
                    OutputDataType wpsOutput = (OutputDataType) processOutput;
                    // retrieve the value of the output ...
                    wpsOutput.getData().getLiteralData().getValue();
                }
            } else if (response.getExecuteResponse().getStatus().getProcessFailed() != null) {
                // Process failed ... handle failed status
            } else if (response.getExecuteResponse().getStatus().getProcessStarted() != null) {
                // Updating status percentage...
                int percentComplete = response.getExecuteResponse().getStatus().getProcessStarted().getPercentCompleted().intValue();
            }
        } else {
                // Retrieve here the Exception message and handle the errored status ...
        }*/
    }

    @Override
    public String defaultId() { return "WPSExecutor"; }

}
