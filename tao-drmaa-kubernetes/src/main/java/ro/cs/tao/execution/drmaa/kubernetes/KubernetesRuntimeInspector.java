package ro.cs.tao.execution.drmaa.kubernetes;

import ro.cs.tao.execution.monitor.NodeRuntimeInspector;
import ro.cs.tao.execution.monitor.RuntimeInfo;
import ro.cs.tao.topology.NodeRole;
import ro.cs.tao.utils.executors.AuthenticationType;

import java.io.IOException;

public class KubernetesRuntimeInspector implements NodeRuntimeInspector {
    @Override
    public boolean isIntendedFor(NodeRole role) {
        return NodeRole.K8S.equals(role);
    }

    @Override
    public void initialize(String host, String user, String password, AuthenticationType authType) throws Exception {

    }

    @Override
    public double getProcessorUsage() throws IOException {
        return 0;
    }

    @Override
    public long getTotalMemoryMB() throws IOException {
        return 0;
    }

    @Override
    public long getAvailableMemoryMB() throws IOException {
        return 0;
    }

    @Override
    public long getTotalDiskGB() throws IOException {
        return 0;
    }

    @Override
    public long getUsedDiskGB() throws IOException {
        return 0;
    }

    @Override
    public RuntimeInfo getInfo() throws Exception {
        return new RuntimeInfo();
    }

    @Override
    public RuntimeInfo getSnapshot() throws Exception {
        return new RuntimeInfo();
    }
}
