package ro.cs.tao.datasource.remote.das.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Results {
    @JsonProperty("@odata.context")
    private String context;
    @JsonProperty("@odata.count")
    private int count;
    @JsonProperty("value")
    private List<Result> results;

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<Result> getResults() {
        return results;
    }

    public void setResults(List<Result> results) {
        this.results = results;
    }
}
