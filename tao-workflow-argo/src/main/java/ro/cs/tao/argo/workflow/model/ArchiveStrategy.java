package ro.cs.tao.argo.workflow.model;

import io.fabric8.kubernetes.api.model.KubernetesResource;

public class ArchiveStrategy implements KubernetesResource {
    private String none;

    public String getNone() {
        return none;
    }

    public void setNone(String none) {
        this.none = none;
    }
}
