package ro.cs.tao.argo.workflow.model;

import java.util.List;

public class DAG {
    private String target;
    private List<DAGTask> tasks;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public List<DAGTask> getTasks() {
        return tasks;
    }

    public void setTasks(List<DAGTask> tasks) {
        this.tasks = tasks;
    }
}
