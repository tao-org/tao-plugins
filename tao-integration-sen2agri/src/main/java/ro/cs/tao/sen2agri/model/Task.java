package ro.cs.tao.sen2agri.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Task {
    private int id;
    private String name;
    private List<Integer> precedingTasksIds;
    private List<Step> steps;

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

    @JsonProperty("preceding_task_ids")
    public List<Integer> getPrecedingTasksIds() {
        return precedingTasksIds;
    }

    public void setPrecedingTasksIds(List<Integer> precedingTasksIds) {
        this.precedingTasksIds = precedingTasksIds;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }
}
