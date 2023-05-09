package ro.cs.tao.sen2agri.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ExternalGraph {
    private int jobId;
    private String outputFolder;
    private String temporaryFolder;
    private String configurationFolder;
    private boolean keepIntermediate;
    private List<ExternalTask> tasks;

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

    public List<ExternalTask> getTasks() {
        if (tasks == null) {
            tasks = new ArrayList<>();
        }
        return tasks;
    }

    public void setTasks(List<ExternalTask> executionTasks) {
        this.tasks = executionTasks;
    }

    public void addTask(ExternalTask task, Integer...parentTaskIds) {
        List<Integer> precedingTaskIds = parentTaskIds != null && parentTaskIds.length > 0 ?
                Arrays.asList(parentTaskIds) : null;
        task.setPrecedingTasksIds(precedingTaskIds);
        getTasks().add(task);
    }
}
