package ro.cs.tao.execution.wps;

import net.opengis.ows10.impl.ExceptionTypeImpl;
import net.opengis.wps10.DataType;
import net.opengis.wps10.ExecuteResponseType;
import net.opengis.wps10.InputDescriptionType;
import net.opengis.wps10.ProcessDescriptionType;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.geotools.data.wps.WPSFactory;
import org.geotools.data.wps.WPSUtils;
import org.geotools.data.wps.WebProcessingService;
import org.geotools.data.wps.request.ExecuteProcessRequest;
import org.geotools.data.wps.response.ExecuteProcessResponse;
import org.geotools.ows.ServiceException;
import org.geotools.process.ProcessFactory;
import org.opengis.util.ProgressListener;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class WPSProcess extends org.geotools.data.wps.WPSProcess {

    private final WebProcessingService webProcessingService;

    protected WPSProcess(ProcessFactory factory, WebProcessingService wps) {
        super(factory);
        this.webProcessingService = wps;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> input, ProgressListener monitor) {

        // Get the describeprocess object so we can use it to build up a request and
        // get the server url to send the request to.
        WPSFactory wpsfactory = (WPSFactory) this.factory;
        ProcessDescriptionType pdt = wpsfactory.getProcessDescriptionType();
        URL url = wpsfactory.getServerURL();

        // create the execute request object
        ExecuteProcessRequest exeRequest = webProcessingService.createExecuteProcessRequest();
        exeRequest.setIdentifier(wpsfactory.getIdentifier());

        // loop through each expected input in the describeprocess, and set it
        // based on what we have in the provided input map.
        EList inputs = pdt.getDataInputs().getInput();
        Iterator iterator = inputs.iterator();
        while (iterator.hasNext()) {
            InputDescriptionType idt = (InputDescriptionType) iterator.next();
            String identifier = idt.getIdentifier().getValue();
            Object inputValue = input.get(identifier);
            if (inputValue != null) {
                // if our value is some sort of collection, then created multiple
                // dataTypes for this inputdescriptiontype.
                List<EObject> list = new ArrayList<>();
                if (inputValue instanceof Map) {
                    for (Object inVal : ((Map) inputValue).values()) {
                        DataType createdInput = WPSUtils.createInputDataType(inVal, idt);
                        list.add(createdInput);
                    }
                } else if (inputValue instanceof Collection) {
                    for (Object inVal : (Collection) inputValue) {
                        DataType createdInput = WPSUtils.createInputDataType(inVal, idt);
                        list.add(createdInput);
                    }
                } else {
                    // our value is a single object so create a single datatype for it
                    DataType createdInput = WPSUtils.createInputDataType(inputValue, idt);
                    list.add(createdInput);
                }
                // add the input to the execute request
                exeRequest.addInput(identifier, list);
            }
        }

        // send the request and get the response
        ExecuteProcessResponse response;
        try {
            response = webProcessingService.issueRequest(exeRequest);
        } catch (ServiceException | IOException e) {
            return null;
        }

        // if there is an exception in the response, return null
        // TODO:  properly handle the exception?
        if (response.getExceptionResponse() != null || response.getExecuteResponse() == null) {
            Object exception = response.getExceptionResponse().getException().get(0);
            String details = "an error";
            if (exception instanceof ExceptionTypeImpl) {
                details = "'" + ((ExceptionTypeImpl) response.getExceptionResponse().getException().get(0)).getExceptionText().get(0) + "'";
            } else if (exception instanceof net.opengis.ows11.impl.ExceptionTypeImpl) {
                details = "'" + ((net.opengis.ows11.impl.ExceptionTypeImpl) response.getExceptionResponse().getException().get(0)).getExceptionText().get(0).toString() + "'";
            }
            throw new RuntimeException("The remote service returned " + details);
        }

        // get response object and create a map of outputs from it
        ExecuteResponseType executeResponse = response.getExecuteResponse();

        // create the result map of outputs
        Map<String, Object> results = new TreeMap<>();
        results = WPSUtils.createResultMap(executeResponse, results);
        if (results.isEmpty()) {
            String statusLocation = executeResponse.getStatusLocation();
            if (statusLocation != null && statusLocation.toLowerCase().contains("jobid")) {
                results.put("JobId", statusLocation.substring(statusLocation.lastIndexOf("=") + 1));
            }
        }
        return results;
    }
}
