package ro.cs.tao.sen2agri.model;

import java.util.List;

public class Graph {
    private int jobId;
    private List<Task> tasks;

    public int getJobId() {
        return jobId;
    }

    public void setJobId(int jobId) {
        this.jobId = jobId;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }
}
