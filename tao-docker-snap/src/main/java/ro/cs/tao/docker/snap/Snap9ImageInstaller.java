package ro.cs.tao.docker.snap;

public class Snap9ImageInstaller extends SnapImageInstaller {

    @Override
    protected String getContainerName() { return "snap-9-0-0"; }

    @Override
    protected String getContainerDescriptorFileName() {
        return "snap9_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "snap9_operators.json";
    }

    @Override
    protected String getLogoFileName() {
        return "snap9_logo.png";
    }

}