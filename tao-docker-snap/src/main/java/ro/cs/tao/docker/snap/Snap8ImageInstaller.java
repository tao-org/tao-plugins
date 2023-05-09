package ro.cs.tao.docker.snap;

public class Snap8ImageInstaller extends SnapImageInstaller {

    @Override
    protected String getContainerName() { return "snap-8-0-0"; }

    @Override
    protected String getContainerDescriptorFileName() {
        return "snap8_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "snap8_operators.json";
    }

    @Override
    protected String getLogoFileName() {
        return "snap_logo.png";
    }

}
