package ro.cs.tao.datasource.remote.odata.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class ContentDate {
    @JsonProperty("Start")
    private LocalDateTime start;
    @JsonProperty("End")
    private LocalDateTime end;

    @JsonProperty("Start")
    public LocalDateTime getStart() {
        return start;
    }
    @JsonProperty("Start")
    public void setStart(LocalDateTime start) {
        this.start = start;
    }
    @JsonProperty("End")
    public LocalDateTime getEnd() {
        return end;
    }
    @JsonProperty("End")
    public void setEnd(LocalDateTime end) {
        this.end = end;
    }
}
