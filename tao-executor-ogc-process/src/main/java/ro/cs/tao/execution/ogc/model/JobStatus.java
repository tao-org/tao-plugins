package ro.cs.tao.execution.ogc.model;

import java.util.ArrayList;
import java.util.List;

public class JobStatus {
    private String jobID;
    private String status;
    private String message;
    private int progress;
    private List<Link> links = new ArrayList<>();

    // Getters and setters
    public String getJobID() { return jobID; }
    public void setJobID(String jobID) { this.jobID = jobID; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public List<Link> getLinks() { return links; }
    public void setLinks(List<Link> links) { this.links = links; }
}