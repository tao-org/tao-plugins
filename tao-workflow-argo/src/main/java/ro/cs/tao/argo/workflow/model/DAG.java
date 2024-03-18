package ro.cs.tao.argo.workflow.model;

import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.List;

public class DAG implements KubernetesResource {
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
