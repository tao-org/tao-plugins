package ro.cs.tao.execution.drmaa.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.execution.monitor.NodeRuntimeInspector;
import ro.cs.tao.execution.monitor.RuntimeInfo;
import ro.cs.tao.topology.NodeRole;
import ro.cs.tao.utils.executors.AuthenticationType;
import ro.cs.tao.utils.executors.MemoryUnit;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class KubernetesRuntimeInspector implements NodeRuntimeInspector {
    private final Logger logger = Logger.getLogger(KubernetesRuntimeInspector.class.getName());
    private io.fabric8.kubernetes.client.Config builder;
    private KubernetesClient client;
    @Override
    public boolean isIntendedFor(NodeRole role) {
        return NodeRole.K8S.equals(role);
    }

    @Override
    public void initialize(String host, String user, String password, AuthenticationType authType) throws Exception {
        boolean hasConfigurationErrors = false;
        Map<String, String> configuration = new HashMap<>();
        try {
            final String url = ConfigurationManager.getInstance().getValue(KubernetesSession.masterURL);
            configuration.put(KubernetesSession.masterURL, url);
            if (url == null) {
                //throw new RuntimeException("Kubernetes master url not found in config file");
                logger.warning("Kubernetes Cluster URL not found in configuration");
                hasConfigurationErrors = true;
            }

            String serviceAccountToken = ConfigurationManager.getInstance().getValue(KubernetesSession.token);
            configuration.put(KubernetesSession.token, serviceAccountToken);
            if (serviceAccountToken == null) {
                //throw new RuntimeException("Kubernetes service account token not found in config file");
                logger.warning("Kubernetes service account token not found in configuration");
                hasConfigurationErrors = true;
            }

            //String namespace = ConfigurationManager.getInstance().getValue(this.namespace);

            String serviceAccountCertFilepath = ConfigurationManager.getInstance().getValue(KubernetesSession.caCertFile);
            configuration.put(KubernetesSession.caCertFile, serviceAccountCertFilepath);
            final Path serviceAccountCertPath = Paths.get(serviceAccountCertFilepath);
            if (!Files.exists(serviceAccountCertPath)) {
                //throw new RuntimeException("No Kubernetes ca certification file found");
                logger.warning("Kubernetes service account certificate file not found");
                hasConfigurationErrors = true;
            }

            if (!hasConfigurationErrors) {
                this.builder = new io.fabric8.kubernetes.client.ConfigBuilder()
                        .withMasterUrl(url)
                        .withNamespace(KubernetesSession.namespace)
                        .withCaCertFile(serviceAccountCertFilepath)
                        .withOauthToken(serviceAccountToken)
                        .build();


                this.client = new KubernetesClientBuilder()
                        .withConfig(this.builder)
                        .build();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double getProcessorUsage() throws IOException {
        AtomicReference<Double> cpuCapacityTotal = new AtomicReference<>((double) 0);
        AtomicReference<Double> cpuAllocatableTotal = new AtomicReference<>((double) 0);
        client.nodes().list().getItems().forEach(node -> {
            BigDecimal cpuCapacity = node.getStatus().getCapacity().get("cpu").getNumericalAmount();
            BigDecimal cpuAllocatable = node.getStatus().getAllocatable().get("cpu").getNumericalAmount();
            cpuCapacityTotal.updateAndGet(v -> (v + cpuCapacity.doubleValue()));
            cpuAllocatableTotal.updateAndGet(v -> (v + cpuAllocatable.doubleValue()));
        });
        return (double) ((cpuCapacityTotal.get() - cpuAllocatableTotal.get()) * 100) / cpuCapacityTotal.get();
    }

    @Override
    public long getTotalMemoryMB() throws IOException {
        AtomicReference<Long> totalMemory = new AtomicReference<>((long) 0);
        client.nodes().list().getItems().forEach(node -> {
            BigDecimal memory = node.getStatus().getCapacity().get("memory").getNumericalAmount();
            totalMemory.updateAndGet(v -> (v + memory.longValue() / MemoryUnit.MB.value()));
        });
        return totalMemory.get();
    }

    @Override
    public long getAvailableMemoryMB() throws IOException {
        AtomicReference<Long> availableMemory = new AtomicReference<>((long) 0);
        client.nodes().list().getItems().forEach(node -> {
            BigDecimal memory = node.getStatus().getAllocatable().get("memory").getNumericalAmount();
            availableMemory.updateAndGet(v -> (v + memory.longValue() / MemoryUnit.MB.value()));
        });
        return availableMemory.get();
    }

    @Override
    public long getTotalDiskGB() throws IOException {
        AtomicReference<Long> totalDisk = new AtomicReference<>((long) 0);
        client.nodes().list().getItems().forEach(node -> {
            // Disk capacity (if available)
            if (node.getStatus().getCapacity().containsKey("ephemeral-storage")) {
                BigDecimal disk = node.getStatus().getCapacity().get("ephemeral-storage").getNumericalAmount();
                totalDisk.updateAndGet(v -> (v + disk.longValue() / MemoryUnit.GB.value()));
            }
        });
        return totalDisk.get();
    }

    @Override
    public long getUsedDiskGB() throws IOException {
        AtomicReference<Long> totalDisk = new AtomicReference<>((long) 0);
        AtomicReference<Long> allocatableDisk = new AtomicReference<>((long) 0);
        client.nodes().list().getItems().forEach(node -> {
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
    }

    @Override
    public RuntimeInfo getInfo() throws Exception {
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
}
