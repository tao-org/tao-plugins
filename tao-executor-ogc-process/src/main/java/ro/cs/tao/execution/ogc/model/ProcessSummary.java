package ro.cs.tao.execution.ogc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The ProcessSummary class represents a summarized description of a process.
 * It encapsulates key details including an identifier, title, description,
 * version information, associated keywords, and links to additional resources.
 *
 * This class provides getter and setter methods to access and modify its attributes.
 */
public class ProcessSummary {
    private String id;
    private String title;
    private String description;
    private String version;
    private List<String> keywords = new ArrayList<>();
    private List<Link> links = new ArrayList<>();

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public List<Link> getLinks() { return links; }
    public void setLinks(List<Link> links) { this.links = links; }
}