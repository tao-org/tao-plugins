package ro.cs.tao.argo.workflow.model;

import io.fabric8.kubernetes.api.model.KubernetesResource;

public class ArchiveStrategy implements KubernetesResource {
    private NoneStrategy none;

    public NoneStrategy getNone() {
        return none;
    }

    public void setNone(NoneStrategy none) {
        this.none = none;
    }
}
