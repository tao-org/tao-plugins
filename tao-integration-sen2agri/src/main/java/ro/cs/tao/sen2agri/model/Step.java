package ro.cs.tao.sen2agri.model;

import java.util.List;

public class Step {
    private String name;
    private List<String> arguments;
    private Integer procs;
    private Integer mem;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }

    public Integer getProcs() {
        return procs;
    }

    public void setProcs(Integer procs) {
        this.procs = procs;
    }

    public Integer getMem() {
        return mem;
    }

    public void setMem(Integer mem) {
        this.mem = mem;
    }
}
