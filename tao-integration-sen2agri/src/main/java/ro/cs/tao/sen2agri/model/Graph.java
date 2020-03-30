package ro.cs.tao.sen2agri.model;

import java.util.List;

public class Graph {
    private int jobId;
    private String outputFolder;
    private String temporaryFolder;
    private String configurationFolder;
    private boolean keepIntermediate;
    private List<Task> tasks;

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public String getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(String outputFolder) {
        this.outputFolder = outputFolder;
    }

    public String getTemporaryFolder() {
        return temporaryFolder;
    }

    public void setTemporaryFolder(String temporaryFolder) {
        this.temporaryFolder = temporaryFolder;
    }

    public String getConfigurationFolder() { return configurationFolder; }

    public void setConfigurationFolder(String configurationFolder) { this.configurationFolder = configurationFolder; }

    public boolean isKeepIntermediate() {
        return keepIntermediate;
    }

    public void setKeepIntermediate(boolean keepIntermediate) {
        this.keepIntermediate = keepIntermediate;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }
}
