package ro.cs.tao.execution.drmaa.kubernetes;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.ggf.drmaa.*;
import ro.cs.tao.EnumUtils;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.execution.DrmaaJobExtensions;
import ro.cs.tao.execution.local.DefaultSession;
import ro.cs.tao.execution.local.DefaultSessionFactory;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;
import ro.cs.tao.topology.NodeDescription;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.utils.executors.ExecutionUnit;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.utils.executors.SSHMode;
import ro.cs.tao.utils.executors.container.ContainerCmdBuilder;
import ro.cs.tao.utils.executors.container.ContainerUnit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class KubernetesSession implements Session {
    private static final String configFileName = "kubernetes.config";
    private static final String namespace = "TAO";
    private final KubernetesClientBuilder builder;
    private KubernetesClient client;
    private Map<String, JobTemplate> jobTemplates;
    private Map<String, Job> runningJobs;
    private Map<String, OutputAccumulator> jobOutputs;
    private AtomicInteger nodeCounter;
    private final Logger logger = Logger.getLogger(DefaultSession.class.getName());

    public KubernetesSession() {
        final Path configPath = ConfigurationManager.getInstance().getConfigurationFolder().resolve(configFileName);
        if (!Files.exists(configPath)) {
            throw new RuntimeException("No Kubernetes configuration file found");
        }
        try {
            Config kubeconfig = Config.fromKubeconfig(configPath.toString());
            this.builder = new KubernetesClientBuilder().withConfig(kubeconfig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String serviceName() { return "Kubernetes"; }

    @Override
    public void init(String contact) throws DrmaaException {
        synchronized (this) {
            this.jobTemplates = Collections.synchronizedMap(new HashMap<>());
            this.runningJobs = Collections.synchronizedMap(new HashMap<>());
            this.jobOutputs = Collections.synchronizedMap(new HashMap<>());
            this.nodeCounter = new AtomicInteger(0);
            this.client = this.builder.build();
        }
    }

    @Override
    public void exit() throws DrmaaException {
        synchronized (this) {
            checkSession();
            this.jobTemplates.clear();
            this.runningJobs.clear();
            this.jobOutputs.clear();
            this.client.close();
            this.client = null;
        }
    }

    @Override
    public JobTemplate createJobTemplate() throws DrmaaException {
        checkSession();
        final JobTemplate jobTemplate = new SimpleJobTemplate() {
            private long softTimeLimit;
            @Override
            public void setSoftRunDurationLimit(long softRunDurationLimit) throws DrmaaException {
                this.softTimeLimit = softRunDurationLimit;
            }

            @Override
            public long getSoftRunDurationLimit() throws DrmaaException {
                return this.softTimeLimit;
            }

            @Override
            protected Set getOptionalAttributeNames() {
                Set set = new HashSet();
                set.add(DrmaaJobExtensions.MEMORY_REQUIREMENTS_ATTRIBUTE);
                set.add(DrmaaJobExtensions.NODE_ATTRIBUTE);
                set.add(DrmaaJobExtensions.CONTAINER_ATTRIBUTE);
                set.add(DrmaaJobExtensions.SIMULATE_EXECUTION_ATTRIBUTE);
                set.add(DrmaaJobExtensions.TASK_NAME);
                set.add(DrmaaJobExtensions.TASK_ID);
                set.add(DrmaaJobExtensions.TASK_ANCESTOR_ID);
                set.add(DrmaaJobExtensions.TASK_ANCESTOR_OUTPUT);
                set.add(DrmaaJobExtensions.TASK_OUTPUT);
                set.add(DrmaaJobExtensions.JOB_ID);
                set.add(DrmaaJobExtensions.USER);
                set.add(DrmaaJobExtensions.SCRIPT_FORMAT);
                set.add(DrmaaJobExtensions.SCRIPT_PATH);
                return set;
            }
        };
        jobTemplate.setJobName(UUID.randomUUID().toString());
        this.jobTemplates.put(jobTemplate.getJobName(), jobTemplate);
        return jobTemplate;
    }

    @Override
    public void deleteJobTemplate(JobTemplate jt) throws DrmaaException {
        checkSession();
        checkRegistered(jt);
        this.jobTemplates.remove(jt.getJobName());
    }

    public int getJobExitCode(String jobId) {
        // todo
        return -1;
    }

    public String getJobOutput(String jobId) throws DrmaaException {
        OutputAccumulator consumer = this.jobOutputs.get(jobId);
        return consumer != null ? consumer.getOutput() : "n/a";
    }

    public void cleanupJob(String jobId) {
        this.jobOutputs.remove(jobId);
        this.runningJobs.remove(jobId);
    }

    @Override
    public String runJob(JobTemplate jt) throws DrmaaException {
        checkSession();
        checkRegistered(jt);
        if (jt.getRemoteCommand() == null || jt.getRemoteCommand().isEmpty() || jt.getArgs() == null) {
            throw new InvalidJobTemplateException();
        }
        synchronized (this) {
            Long memConstraint = null;
            NodeDescription node = null;
            final List<String> args = new ArrayList<>();
            ExecutionUnitFormat format = ExecutionUnitFormat.TAO;
            if (jt instanceof JobTemplateExtension) {
                JobTemplateExtension job = (JobTemplateExtension) jt;
                if (job.hasAttribute(DrmaaJobExtensions.MEMORY_REQUIREMENTS_ATTRIBUTE)) {
                    Object value = job.getAttribute(DrmaaJobExtensions.MEMORY_REQUIREMENTS_ATTRIBUTE);
                    memConstraint = value != null ? Long.parseLong(value.toString()) : null;
                }
                if (job.hasAttribute(DrmaaJobExtensions.CONTAINER_ATTRIBUTE)) {
                    ContainerUnit unit = (ContainerUnit) job.getAttribute(DrmaaJobExtensions.CONTAINER_ATTRIBUTE);
                    args.addAll(ContainerCmdBuilder.buildCommandLineArguments(unit));
                }
            }
            args.add(jt.getRemoteCommand());
            args.addAll(jt.getArgs());
            final ExecutionUnit unit = new ExecutionUnit(ExecutorType.SSH2, jt.getJobName(), null, null,
                                                         args, false, SSHMode.EXEC,
                                                         false,
                                                         jt.getJobName() + "_" + System.nanoTime(), ExecutionUnitFormat.TAO);
            if (memConstraint != null) {
                unit.setMinMemory(memConstraint);
            }
            final String jobId = jt.getJobName() + ":" + System.nanoTime();
            this.runningJobs.put(jobId, execute(jobId, unit));
            return jobId;
        }
    }

    @Override
    public List runBulkJobs(JobTemplate jt, int start, int end, int incr) throws DrmaaException {
        throw new DeniedByDrmException("Not supported");
    }

    @Override
    public void control(String jobId, int action) throws DrmaaException {
        checkSession();
        Job job = this.runningJobs.get(jobId);
        Pod pod = job != null ? getPod(job) : null;
        switch (action) {
            case HOLD:
            case SUSPEND:
                if (job == null) {
                    throw new NoActiveSessionException("No active executor for job " + jobId);
                }
                if (!"Running".equals(pod.getStatus().getPhase())) {
                    throw new HoldInconsistentStateException();
                }
                // TODO: suspend the pod execution
                break;
            case TERMINATE:
                if (job == null) {
                    throw new NoActiveSessionException("No active executor for job " + jobId);
                }
                stopPods(job);
                break;
            case RELEASE:
            case RESUME:
                if (job == null) {
                    throw new NoActiveSessionException("No active executor for job " + jobId);
                }
                // todo
                break;
        }
    }

    @Override
    public void synchronize(List jobIds, long timeout, boolean dispose) throws DrmaaException {
        throw new RuntimeException("Not supported");
    }

    @Override
    public JobInfo wait(String jobId, long timeout) throws DrmaaException {
        throw new RuntimeException("Not supported");
    }

    @Override
    public int getJobProgramStatus(String jobId) throws DrmaaException {
        checkSession();
        Job job = this.runningJobs.get(jobId);
        if (job == null) {
            throw new InvalidJobException();
        }
        Pod pod = getPod(job);
        PodStatus status = EnumUtils.getEnumConstantByFriendlyName(PodStatus.class, pod.getStatus().getPhase());
        if (status == null) {
            status = PodStatus.UNKNOWN;
        }
        final int statusId;
        switch (status) {
            case PENDING:
                statusId = QUEUED_ACTIVE;
                break;
            case RUNNING:
                statusId = RUNNING;
                break;
            case SUCCEEDED:
                statusId = DONE;
                break;
            case FAILED:
                statusId = FAILED;
                break;
            case UNKNOWN:
            default:
                statusId = UNDETERMINED;
                break;
        }
        return statusId;
    }

    @Override
    public String getContact() {
        return null;
    }

    @Override
    public Version getVersion() {
        return new Version(1, 0);
    }

    @Override
    public String getDrmSystem() {
        List<String> drmFactories = new ArrayList<>();
        if (this.client == null) {
            ServiceRegistry<SessionFactory> serviceRegistry =
                    ServiceRegistryManager.getInstance().getServiceRegistry(SessionFactory.class);
            if (serviceRegistry != null) {
                Set<SessionFactory> services = serviceRegistry.getServices();
                if (services != null) {
                    drmFactories.addAll(services.stream()
                                                .map(sf -> sf.getClass().getPackage().getName())
                                                .collect(Collectors.toList()));
                }
            }
        }
        return String.join(",", drmFactories);
    }

    @Override
    public String getDrmaaImplementation() {
        List<String> drmFactories = new ArrayList<>();
        drmFactories.add(DefaultSessionFactory.class.getName());
        if (this.client == null) {
            ServiceRegistry<SessionFactory> serviceRegistry =
                    ServiceRegistryManager.getInstance().getServiceRegistry(SessionFactory.class);
            if (serviceRegistry != null) {
                Set<SessionFactory> services = serviceRegistry.getServices();
                if (services != null) {
                    drmFactories.addAll(services.stream()
                                                .map(sf -> sf.getClass().getName())
                                                .collect(Collectors.toList()));
                }
            }
        }
        return String.join(",", drmFactories);
    }

    private void checkSession() throws NoActiveSessionException {
        if (this.client == null) {
            throw new NoActiveSessionException();
        }
    }

    private void checkRegistered(JobTemplate jobTemplate) throws DrmaaException {
        if (jobTemplate == null) {
            throw new IllegalArgumentException("JobTemplate cannot be null");
        }
        String jobName = jobTemplate.getJobName();
        if (!this.jobTemplates.containsKey(jobName)) {
            throw new InvalidJobTemplateException();
        }
    }

    private Job execute(String jobId, ExecutionUnit unit) {
        Container container = new Container();
        ContainerUnit containerUnit = unit.getContainerUnit();
        container.setName(containerUnit.getContainerName());
        container.setImage(containerUnit.getContainerName());
        container.setArgs(unit.getArguments());
        container.setCommand(unit.getArguments());
        if (containerUnit.getVolumeMap() != null) {
            final List<VolumeMount> mounts = new ArrayList<>();
            for (Map.Entry<String, String> entry : containerUnit.getVolumeMap().entrySet()) {
                VolumeMount mount = new VolumeMount();
                mount.setName(entry.getKey());
                mount.setMountPath(entry.getValue());
                mounts.add(mount);
            }
            container.setVolumeMounts(mounts);
        }
        ResourceRequirements resourceRequirements = new ResourceRequirements();
        resourceRequirements.setAdditionalProperty("memory", new Quantity(unit.getMinMemory() + "Mi"));
        container.setResources(resourceRequirements);
        final Job job = new JobBuilder()
                    .withApiVersion("batch/v1")
                    .withNewMetadata()
                        .withName(jobId)
                        //.withLabels(Collections.singletonMap("Label", //put job label here))
                    .endMetadata()
                    .withNewSpec()
                        .withNewTemplate()
                            .withNewSpec()
                                .addNewContainerLike(container)
                                .endContainer()
                                .withRestartPolicy("Never")
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                    .build();
        client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
        return job;
    }

    private Pod getPod(Job job) {
        // Get All pods created by the job
        PodList podList = client.pods().inNamespace(namespace).withLabel("job-name", job.getMetadata().getName()).list();
        // Wait for pod to complete
        return client.pods().inNamespace(namespace).withName(podList.getItems().get(0).getMetadata().getName()).get();
    }

    private void stopPods(Job job) {
        // Get All pods created by the job
        PodList podList = client.pods().inNamespace(namespace).withLabel("job-name", job.getMetadata().getName()).list();
        // Wait for pod to complete
        for (Pod pod : podList.getItems()) {
            client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()).delete();
        }
    }
}