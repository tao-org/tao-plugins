package ro.cs.tao.execution.drmaa.kubernetes;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import ro.cs.tao.execution.monitor.NodeRuntimeInspector;
import ro.cs.tao.execution.monitor.RuntimeInfo;
import ro.cs.tao.topology.NodeRole;
import ro.cs.tao.utils.executors.AuthenticationType;
import ro.cs.tao.utils.executors.MemoryUnit;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class KubernetesRuntimeInspector implements NodeRuntimeInspector {
    private final Logger logger = Logger.getLogger(KubernetesRuntimeInspector.class.getName());
    private KubernetesClient client;
    @Override
    public boolean isIntendedFor(NodeRole role) {
        return NodeRole.K8S.equals(role);
    }

    @Override
    public void initialize(String host, String user, String password, AuthenticationType authType) throws Exception {
        Map<String, String> configuration = KubernetesSession.getConfiguration();
        this.client = KubernetesSession.getClient(configuration);
    }

    @Override
    public double getProcessorUsage() throws IOException {
        if(this.client == null) return 0;
        try {
            AtomicReference<Double> cpuCapacityTotal = new AtomicReference<>((double) 0);
            AtomicReference<Double> cpuAllocatableTotal = new AtomicReference<>((double) 0);
            this.client.nodes().list().getItems().forEach(node -> {
                BigDecimal cpuCapacity = node.getStatus().getCapacity().get("cpu").getNumericalAmount();
                BigDecimal cpuAllocatable = node.getStatus().getAllocatable().get("cpu").getNumericalAmount();
                cpuCapacityTotal.updateAndGet(v -> (v + cpuCapacity.doubleValue()));
                cpuAllocatableTotal.updateAndGet(v -> (v + cpuAllocatable.doubleValue()));
            });
            return (double) ((cpuCapacityTotal.get() - cpuAllocatableTotal.get()) * 100) / cpuCapacityTotal.get();
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return 0;
        }
    }

    @Override
    public long getTotalMemoryMB() throws IOException {
        if(this.client == null) return 0;
        try {
            AtomicReference<Long> totalMemory = new AtomicReference<>((long) 0);
            this.client.nodes().list().getItems().forEach(node -> {
                BigDecimal memory = node.getStatus().getCapacity().get("memory").getNumericalAmount();
                totalMemory.updateAndGet(v -> (v + memory.longValue() / MemoryUnit.MB.value()));
            });
            return totalMemory.get();
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return 0;
        }
    }

    @Override
    public long getAvailableMemoryMB() throws IOException {
        if(this.client == null) return 0;
        try {
            AtomicReference<Long> availableMemory = new AtomicReference<>((long) 0);
            this.client.nodes().list().getItems().forEach(node -> {
                BigDecimal memory = node.getStatus().getAllocatable().get("memory").getNumericalAmount();
                availableMemory.updateAndGet(v -> (v + memory.longValue() / MemoryUnit.MB.value()));
            });
            return availableMemory.get();
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return 0;
        }
    }

    @Override
    public long getTotalDiskGB() throws IOException {
        if(this.client == null) return 0;
        try {
            AtomicReference<Long> totalDisk = new AtomicReference<>((long) 0);
            this.client.nodes().list().getItems().forEach(node -> {
                // Disk capacity (if available)
                if (node.getStatus().getCapacity().containsKey("ephemeral-storage")) {
                    BigDecimal disk = node.getStatus().getCapacity().get("ephemeral-storage").getNumericalAmount();
                    totalDisk.updateAndGet(v -> (v + disk.longValue() / MemoryUnit.GB.value()));
                }
            });
            return totalDisk.get();
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return 0;
        }
    }

    @Override
    public long getUsedDiskGB() throws IOException {
        if(this.client == null) return 0;
        try {
            AtomicReference<Long> totalDisk = new AtomicReference<>((long) 0);
            AtomicReference<Long> allocatableDisk = new AtomicReference<>((long) 0);
            this.client.nodes().list().getItems().forEach(node -> {
                // Disk capacity (if available)
                if (node.getStatus().getCapacity().containsKey("ephemeral-storage")) {
                    BigDecimal disk = node.getStatus().getCapacity().get("ephemeral-storage").getNumericalAmount();
                    totalDisk.updateAndGet(v -> (v + disk.longValue() / MemoryUnit.GB.value()));
                }
                // Disk allocatable (if available)
                if (node.getStatus().getAllocatable().containsKey("ephemeral-storage")) {
                    BigDecimal disk = node.getStatus().getCapacity().get("ephemeral-storage").getNumericalAmount();
                    allocatableDisk.updateAndGet(v -> (v + disk.longValue() / MemoryUnit.GB.value()));
                }
            });
            return totalDisk.get() - allocatableDisk.get();
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return 0;
        }
    }

    @Override
    public RuntimeInfo getInfo() throws Exception {
        return readInfo();
    }

    @Override
    public RuntimeInfo getSnapshot() throws Exception {
        RuntimeInfo runtimeInfo = new RuntimeInfo();
        runtimeInfo.setCpuTotal(getProcessorUsage());
        runtimeInfo.setTotalMemory(getTotalMemoryMB());
        runtimeInfo.setAvailableMemory(getAvailableMemoryMB());
        runtimeInfo.setDiskTotal(getTotalDiskGB());
        runtimeInfo.setDiskUsed(getUsedDiskGB());
        runtimeInfo.setDiskUnit(MemoryUnit.GB);
        runtimeInfo.setMemoryUnit(MemoryUnit.MB);
        return runtimeInfo;
    }

    private RuntimeInfo readInfo() throws Exception {
        if (this.client == null) {
            return null;
        }
        RuntimeInfo runtimeInfo = null;
        try {
            AtomicReference<Double> cpuCapacityTotal = new AtomicReference<>((double) 0);
            AtomicReference<Double> cpuAllocatableTotal = new AtomicReference<>((double) 0);
            AtomicReference<Long> totalMemory = new AtomicReference<>((long) 0);
            AtomicReference<Long> availableMemory = new AtomicReference<>((long) 0);
            AtomicReference<Long> totalDisk = new AtomicReference<>((long) 0);
            AtomicReference<Long> allocatableDisk = new AtomicReference<>((long) 0);
            final NonNamespaceOperation<Node, NodeList, Resource<Node>> nodes = this.client.nodes();
            if (nodes != null) {
                runtimeInfo = new RuntimeInfo();
                nodes.list().getItems().forEach(node -> {
                    BigDecimal cpuCapacity = node.getStatus().getCapacity().get("cpu").getNumericalAmount();
                    BigDecimal cpuAllocatable = node.getStatus().getAllocatable().get("cpu").getNumericalAmount();
                    cpuCapacityTotal.updateAndGet(v -> (v + cpuCapacity.doubleValue()));
                    cpuAllocatableTotal.updateAndGet(v -> (v + cpuAllocatable.doubleValue()));

                    BigDecimal memory = node.getStatus().getCapacity().get("memory").getNumericalAmount();
                    totalMemory.updateAndGet(v -> (v + memory.longValue() / MemoryUnit.MB.value()));

                    BigDecimal aMemory = node.getStatus().getAllocatable().get("memory").getNumericalAmount();
                    availableMemory.updateAndGet(v -> (v + aMemory.longValue() / MemoryUnit.MB.value()));
                });
                runtimeInfo.setCpuTotal((double) ((cpuCapacityTotal.get() - cpuAllocatableTotal.get()) * 100) / cpuCapacityTotal.get());
                runtimeInfo.setTotalMemory(totalMemory.get());
                runtimeInfo.setAvailableMemory(availableMemory.get());
                runtimeInfo.setDiskTotal(totalDisk.get());
                runtimeInfo.setDiskUsed(totalDisk.get() - allocatableDisk.get());
                runtimeInfo.setDiskUnit(MemoryUnit.GB);
                runtimeInfo.setMemoryUnit(MemoryUnit.MB);
            }
            return runtimeInfo;
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return null;
        }
    }
}
