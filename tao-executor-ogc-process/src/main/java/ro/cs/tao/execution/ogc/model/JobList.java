package ro.cs.tao.execution.ogc.model;

import java.util.ArrayList;
import java.util.List;

public class JobList {
    private List<JobStatus> jobs = new ArrayList<>();
    private List<Link> links = new ArrayList<>();

    public List<JobStatus> getJobs() { return jobs; }
    public void setJobs(List<JobStatus> jobs) { this.jobs = jobs; }
    public List<Link> getLinks() { return links; }
    public void setLinks(List<Link> links) { this.links = links; }
}