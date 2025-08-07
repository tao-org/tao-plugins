package ro.cs.tao.execution.drmaa.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceList;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import ro.cs.tao.execution.monitor.NodeRuntimeInspector;
import ro.cs.tao.execution.monitor.RuntimeInfo;
import ro.cs.tao.execution.monitor.RuntimeInfoCluster;
import ro.cs.tao.topology.NodeRole;
import ro.cs.tao.utils.executors.AuthenticationType;
import ro.cs.tao.utils.executors.MemoryUnit;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
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
            Map<String, Map<String, BigDecimal>> usageMetrics = this.getUsageMetrics();
            if(usageMetrics == null) return 0;

            AtomicReference<Double> cpuUsageTotal = new AtomicReference<>((double) 0);
            AtomicReference<Double> cpuCapacityTotal = new AtomicReference<>((double) 0);
            this.client.nodes().list().getItems().forEach(node -> {
                if(usageMetrics.containsKey(node.getMetadata().getName())) {
                    BigDecimal cpuCapacity = node.getStatus().getCapacity().get("cpu").getNumericalAmount();
                    cpuCapacityTotal.updateAndGet(v -> (v + cpuCapacity.doubleValue()));
                    cpuUsageTotal.updateAndGet(v -> (v + usageMetrics.get(node.getMetadata().getName()).get("cpu").doubleValue()));
                }
            });
            return (double) (cpuUsageTotal.get() * 100) / cpuCapacityTotal.get();
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
                Map<String, String> labels = node.getMetadata().getLabels();
                if (!(labels.containsKey("node-role.kubernetes.io/master") ||
                        labels.containsKey("node-role.kubernetes.io/control-plane"))) {
                    BigDecimal memory = node.getStatus().getCapacity().get("memory").getNumericalAmount();
                    totalMemory.updateAndGet(v -> (v + memory.longValue() / MemoryUnit.MB.value()));
                }
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
            Map<String, Map<String, BigDecimal>> usageMetrics = this.getUsageMetrics();
            if(usageMetrics == null) return 0;

            AtomicReference<Long> totalMemory = new AtomicReference<>((long) 0);
            AtomicReference<Long> usedMemory = new AtomicReference<>((long) 0);
            this.client.nodes().list().getItems().forEach(node -> {
                if(usageMetrics.containsKey(node.getMetadata().getName())) {
                    BigDecimal memory = node.getStatus().getAllocatable().get("memory").getNumericalAmount();
                    totalMemory.updateAndGet(v -> (v + memory.longValue() / MemoryUnit.MB.value()));
                    usedMemory.updateAndGet(v -> (v + usageMetrics.get(node.getMetadata().getName()).get("memory").longValue() / MemoryUnit.MB.value()));
                }
            });
            return totalMemory.get() - usedMemory.get();
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
                Map<String, String> labels = node.getMetadata().getLabels();
                if (!(labels.containsKey("node-role.kubernetes.io/master") ||
                        labels.containsKey("node-role.kubernetes.io/control-plane"))) {
                    // Disk capacity (if available)
                    if (node.getStatus().getCapacity().containsKey("ephemeral-storage")) {
                        BigDecimal disk = node.getStatus().getCapacity().get("ephemeral-storage").getNumericalAmount();
                        totalDisk.updateAndGet(v -> (v + disk.longValue() / MemoryUnit.GB.value()));
                    }
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
        // TODO this cannot be implemented using Fabric8
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
            Map<String, Map<String, BigDecimal>> usageMetrics = this.getUsageMetrics();
            if(usageMetrics == null) return null;

            AtomicReference<Double> cpuCapacityTotal = new AtomicReference<>((double) 0);
            AtomicReference<Double> cpuUsageTotal = new AtomicReference<>((double) 0);
            AtomicReference<Long> totalMemory = new AtomicReference<>((long) 0);
            AtomicReference<Long> usedMemory = new AtomicReference<>((long) 0);
            AtomicReference<Long> totalDisk = new AtomicReference<>((long) 0);
            final NonNamespaceOperation<Node, NodeList, Resource<Node>> nodes = this.client.nodes();
            if (nodes != null) {
                runtimeInfo = new RuntimeInfo();
                nodes.list().getItems().forEach(node -> {
                    if(usageMetrics.containsKey(node.getMetadata().getName())) {
                        BigDecimal cpuCapacity = node.getStatus().getCapacity().get("cpu").getNumericalAmount();
                        cpuCapacityTotal.updateAndGet(v -> (v + cpuCapacity.doubleValue()));
                        cpuUsageTotal.updateAndGet(v -> (v + usageMetrics.get(node.getMetadata().getName()).get("cpu").doubleValue()));

                        BigDecimal memory = node.getStatus().getCapacity().get("memory").getNumericalAmount();
                        totalMemory.updateAndGet(v -> (v + memory.longValue() / MemoryUnit.MB.value()));
                        usedMemory.updateAndGet(v -> (v + usageMetrics.get(node.getMetadata().getName()).get("memory").longValue() / MemoryUnit.MB.value()));

                        // Disk capacity (if available)
                        if (node.getStatus().getCapacity().containsKey("ephemeral-storage")) {
                            BigDecimal disk = node.getStatus().getCapacity().get("ephemeral-storage").getNumericalAmount();
                            totalDisk.updateAndGet(v -> (v + disk.longValue() / MemoryUnit.GB.value()));
                        }
                    }
                });
                runtimeInfo.setCpuTotal((cpuUsageTotal.get() * 100) / cpuCapacityTotal.get());
                runtimeInfo.setTotalMemory(totalMemory.get());
                runtimeInfo.setAvailableMemory(totalMemory.get() - usedMemory.get());
                runtimeInfo.setDiskTotal(totalDisk.get());
                runtimeInfo.setDiskUsed(0);
                runtimeInfo.setDiskUnit(MemoryUnit.GB);
                runtimeInfo.setMemoryUnit(MemoryUnit.MB);
            }
            return runtimeInfo;
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return null;
        }
    }

    @Override
    public RuntimeInfoCluster getInfoCluster() throws Exception {
        return readInfoCluster();
    }

    @Override
    public RuntimeInfoCluster getSnapshotCluster() throws Exception {
        return readInfoCluster();
    }

    private RuntimeInfoCluster readInfoCluster() throws Exception {
        if (this.client == null) {
            return null;
        }
        RuntimeInfoCluster runtimeInfoCluster = new RuntimeInfoCluster();
        RuntimeInfo clusterInfo = new RuntimeInfo();
        try {
            Map<String, Map<String, BigDecimal>> usageMetrics = this.getUsageMetrics();
            if(usageMetrics == null) return null;

            AtomicReference<Double> cpuCapacityTotal = new AtomicReference<>((double) 0);
            AtomicReference<Double> cpuUsageTotal = new AtomicReference<>((double) 0);
            AtomicReference<Long> totalMemory = new AtomicReference<>((long) 0);
            AtomicReference<Long> usedMemory = new AtomicReference<>((long) 0);
            AtomicReference<Long> totalDisk = new AtomicReference<>((long) 0);
            final NonNamespaceOperation<Node, NodeList, Resource<Node>> nodes = this.client.nodes();
            if (nodes != null) {
                Map<String, RuntimeInfo> nodesInfo = new HashMap<>();
                nodes.list().getItems().forEach(node -> {
                    if(usageMetrics.containsKey(node.getMetadata().getName())) {
                        RuntimeInfo nodeInfo = new RuntimeInfo();
                        BigDecimal cpuCapacity = node.getStatus().getCapacity().get("cpu").getNumericalAmount();
                        cpuCapacityTotal.updateAndGet(v -> (v + cpuCapacity.doubleValue()));
                        cpuUsageTotal.updateAndGet(v -> (v + usageMetrics.get(node.getMetadata().getName()).get("cpu").doubleValue()));

                        BigDecimal memory = node.getStatus().getCapacity().get("memory").getNumericalAmount();
                        totalMemory.updateAndGet(v -> (v + memory.longValue() / MemoryUnit.MB.value()));
                        usedMemory.updateAndGet(v -> (v + usageMetrics.get(node.getMetadata().getName()).get("memory").longValue() / MemoryUnit.MB.value()));

                        // Disk capacity (if available)
                        if (node.getStatus().getCapacity().containsKey("ephemeral-storage")) {
                            BigDecimal disk = node.getStatus().getCapacity().get("ephemeral-storage").getNumericalAmount();
                            totalDisk.updateAndGet(v -> (v + disk.longValue() / MemoryUnit.GB.value()));
                            nodeInfo.setDiskTotal(disk.longValue() / MemoryUnit.GB.value());
                        }

                        nodeInfo.setCpuTotal((usageMetrics.get(node.getMetadata().getName()).get("cpu").doubleValue() * 100) / cpuCapacity.longValue());
                        nodeInfo.setTotalMemory(memory.longValue() / MemoryUnit.MB.value());
                        nodeInfo.setAvailableMemory((memory.longValue() - usageMetrics.get(node.getMetadata().getName()).get("memory").longValue()) / MemoryUnit.MB.value());
                        nodeInfo.setDiskUsed(0);
                        nodeInfo.setDiskUnit(MemoryUnit.GB);
                        nodeInfo.setMemoryUnit(MemoryUnit.MB);
                        nodesInfo.put(node.getMetadata().getName(), nodeInfo);
                    }
                });
                clusterInfo.setCpuTotal((cpuUsageTotal.get() * 100) / cpuCapacityTotal.get());
                clusterInfo.setTotalMemory(totalMemory.get());
                clusterInfo.setAvailableMemory(totalMemory.get() - usedMemory.get());
                clusterInfo.setDiskTotal(totalDisk.get());
                clusterInfo.setDiskUsed(0);
                clusterInfo.setDiskUnit(MemoryUnit.GB);
                clusterInfo.setMemoryUnit(MemoryUnit.MB);

                runtimeInfoCluster.setClusterInfo(clusterInfo);
                runtimeInfoCluster.setNodesInfo(nodesInfo);
            }

            return runtimeInfoCluster;
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return null;
        }
    }

    /**
     * Returns usage metrics for worker nodes (master nodes are skipped), cpu and memory
     * @return Map(nodeName, Map(cpu|memory, value))
     */
    private Map<String, Map<String, BigDecimal>> getUsageMetrics() {
        if (this.client == null) {
            return null;
        }
        Map<String, Map<String, BigDecimal>> result = new HashMap<>();
        try {
            final NonNamespaceOperation<Node, NodeList, Resource<Node>> nodes = this.client.nodes();
            if (nodes != null) {
                nodes.list().getItems().forEach(node -> {
                    Map<String, String> labels = node.getMetadata().getLabels();
                    if (!(labels.containsKey("node-role.kubernetes.io/master") ||
                            labels.containsKey("node-role.kubernetes.io/control-plane"))) {
                        result.put(node.getMetadata().getName(), null);
                    }
                });
            }
            CustomResourceDefinitionContext metricsContext = new CustomResourceDefinitionContext.Builder()
                    .withGroup("metrics.k8s.io")
                    .withVersion("v1beta1")
                    .withPlural("nodes")
                    .withScope("Cluster")
                    .build();

            MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> crClient =
                    this.client.genericKubernetesResources(metricsContext);

            GenericKubernetesResourceList metricsList = crClient.list();

            metricsList.getItems().forEach(nodeMetric -> {
                String nodeName = nodeMetric.getMetadata().getName();
                if(result.containsKey(nodeName)) {
                    Object usage = nodeMetric.getAdditionalProperties().get("usage");
                    if (usage instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> usageMap = (Map<String, String>) usage;
                        BigDecimal cpu = QuantityParser.parseQuantity(usageMap.get("cpu"), QuantityParser.QuantityType.CPU);
                        BigDecimal memory = QuantityParser.parseQuantity(usageMap.get("memory"), QuantityParser.QuantityType.MEMORY);
                        result.replace(nodeName, Map.of("cpu", cpu, "memory", memory));

//                        System.out.printf("Node: %s | CPU: %s | Memory: %s%n", nodeName, cpu, memory);
                    }
                }
            });

            return result;
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return null;
        }

    }
}
