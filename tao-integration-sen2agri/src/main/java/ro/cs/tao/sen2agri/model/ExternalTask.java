package ro.cs.tao.sen2agri.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ExternalTask {
    private int id;
    private String name;
    private boolean adHoc;
    private List<Integer> precedingTasksIds;
    private List<ExternalStep> steps;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAdHoc() { return adHoc; }

    public void setAdHoc(boolean adHoc) { this.adHoc = adHoc; }

    @JsonProperty("preceding_task_ids")
    public List<Integer> getPrecedingTasksIds() {
        return precedingTasksIds;
    }

    public void setPrecedingTasksIds(List<Integer> precedingTasksIds) {
        this.precedingTasksIds = precedingTasksIds;
    }

    public List<ExternalStep> getSteps() {
        if (steps == null) {
            steps = new ArrayList<>();
        }
        return steps;
    }

    public void setSteps(List<ExternalStep> steps) {
        this.steps = steps;
    }

    public void addStep(ExternalStep executionStep) {
        getSteps().add(executionStep);
    }
}
