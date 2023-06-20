package ro.cs.tao.docker.jupyter;

import ro.cs.tao.topology.docker.BaseSingletonInstaller;

import java.util.List;

public class JupyterContainer extends BaseSingletonInstaller {

    public JupyterContainer() {
        super();
    }

    @Override
    protected List<String> startupArguments() {
        return null;
    }

    @Override
    protected String logoFile() {
        return null;
    }

    @Override
    protected String descriptor() {
        return "Jupyter Lite";
    }

    @Override
    public boolean isPerUser() {
        return true;
    }

    @Override
    public String getContainerName() {
        return "jupyterlite";
    }

    @Override
    public String getContainerDescription() {
        return "Container with a Jupyter Lite environment";
    }
}
