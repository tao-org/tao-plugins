package ro.cs.tao.component;

import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.component.validation.ValidationException;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@XmlRootElement(name = "wpsComponent")
public class WPSComponent extends TaoComponent {
    private String remoteAddress;
    private String capabilityName;
    private List<ParameterDescriptor> parameters;
    private ProcessingComponentVisibility visibility;
    private boolean active;
    private String owner;

    public WPSComponent() { }

    private WPSComponent(WPSComponent source) throws CloneNotSupportedException {
        this.remoteAddress = source.remoteAddress;
        this.capabilityName = source.capabilityName;
        if (source.parameters != null) {
            this.parameters = new ArrayList<>();
            for (ParameterDescriptor parameterDescriptor : source.parameters) {
                this.parameters.add(parameterDescriptor.clone());
            }
        }
        this.visibility = source.visibility;
        this.active = source.active;
        this.owner = source.owner;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public String getCapabilityName() {
        return capabilityName;
    }

    public void setCapabilityName(String capabilityName) {
        this.capabilityName = capabilityName;
    }

    public List<ParameterDescriptor> getParameters() {
        if (parameters == null) {
            parameters = new ArrayList<>();
        }
        return parameters;
    }

    public void setParameters(List<ParameterDescriptor> parameters) {
        this.parameters = parameters;
    }

    public ProcessingComponentVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(ProcessingComponentVisibility visibility) {
        this.visibility = visibility;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    /**
     * Validates the parameter values agains the parameter descriptors.
     */
    public void validate(Map<String, Object> parameterValues) throws ValidationException {
        if (parameterValues != null) {
            final List<ParameterDescriptor> parameterDescriptors =
                    getParameters().stream()
                            .filter(d -> parameterValues.containsKey(d.getId()))
                            .collect(Collectors.toList());
            for (ParameterDescriptor descriptor : parameterDescriptors) {
                descriptor.validate(parameterValues.get(descriptor.getId()));
            }
        }
    }

    @Override
    public WPSComponent clone() throws CloneNotSupportedException {
        return new WPSComponent(this);
    }
}
