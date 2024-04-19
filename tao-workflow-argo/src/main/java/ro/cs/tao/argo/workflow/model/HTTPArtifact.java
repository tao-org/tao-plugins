package ro.cs.tao.argo.workflow.model;

import java.io.Serializable;

public class HTTPArtifact implements Serializable {
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
