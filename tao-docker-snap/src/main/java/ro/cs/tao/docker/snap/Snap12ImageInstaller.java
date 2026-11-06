package ro.cs.tao.docker.snap;

public class Snap12ImageInstaller extends SnapImageInstaller {

    @Override
    public String getContainerName() { return "snap-12-0-0"; }

    @Override
    protected String getContainerDescriptorFileName() {
        return "snap12_container.json";
    }

    @Override
    protected String getComponentDescriptorFileName() {
        return "snap12_operators.json";
    }

    @Override
    protected String getLogoFileName() {
        return "snap12_logo.png";
    }

}