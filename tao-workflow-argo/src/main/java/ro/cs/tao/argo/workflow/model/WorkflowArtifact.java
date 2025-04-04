package ro.cs.tao.argo.workflow.model;

public class WorkflowArtifact {

    private String name;
    private String globalName;
    private HTTPArtifact http;
    private String path;
    private ArchiveStrategy archive;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGlobalName() {
        return globalName;
    }

    public void setGlobalName(String globalName) {
        this.globalName = globalName;
    }

    public HTTPArtifact getHttp() {
        return http;
    }

    public void setHttp(HTTPArtifact http) {
        this.http = http;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public ArchiveStrategy getArchive() {
        return archive;
    }

    public void setArchive(ArchiveStrategy archive) {
        this.archive = archive;
    }
}
