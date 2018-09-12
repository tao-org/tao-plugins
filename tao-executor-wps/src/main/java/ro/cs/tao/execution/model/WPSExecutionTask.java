package ro.cs.tao.execution.model;

import ro.cs.tao.component.Variable;
import ro.cs.tao.component.WPSComponent;

import java.util.ArrayList;

public class WPSExecutionTask extends ExecutionTask {

    private WPSComponent wpsComponent;

    public WPSExecutionTask() { super(); }

    public WPSComponent getWpsComponent() { return wpsComponent; }

    public void setWpsComponent(WPSComponent wpsComponent) { this.wpsComponent = wpsComponent; }

    @Override
    public void setInputParameterValue(String parameterId, String value) {

    }

    @Override
    public void setOutputParameterValue(String parameterId, String value) {
        if (this.outputParameterValues == null) {
            this.outputParameterValues = new ArrayList<>();
        }
        this.outputParameterValues.add(new Variable(parameterId, value));
    }

    @Override
    public String buildExecutionCommand() {
        return null;
    }
}
