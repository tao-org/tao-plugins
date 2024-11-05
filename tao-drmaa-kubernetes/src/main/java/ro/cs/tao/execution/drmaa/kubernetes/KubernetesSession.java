package ro.cs.tao.execution.drmaa.kubernetes;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import org.apache.commons.lang3.StringUtils;
import org.ggf.drmaa.*;
import ro.cs.tao.EnumUtils;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.execution.DrmaaJobExtensions;
import ro.cs.tao.execution.drmaa.JobExitHandler;
import ro.cs.tao.execution.local.DefaultSessionFactory;
import ro.cs.tao.serialization.JsonMapper;
import ro.cs.tao.spi.ServiceRegistry;
import ro.cs.tao.spi.ServiceRegistryManager;
import ro.cs.tao.topology.NodeDescription;
import ro.cs.tao.utils.ExecutionUnitFormat;
import ro.cs.tao.utils.executors.ExecutionUnit;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.utils.executors.SSHMode;
import ro.cs.tao.utils.executors.container.ContainerType;
import ro.cs.tao.utils.executors.container.ContainerUnit;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class KubernetesSession implements Session, JobExitHandler {
    static final String configFileName = "kubernetes.config";
    static final String token = "tao.kubernetes.token";
    static final String namespace = "argo";
    static final String masterURL = "tao.kubernetes.master.url";
    static final String caCertFile = "tao.kubernetes.ca.cert.file";
    static final String pvcMappingsKey = "tao.kubernetes.pvc.mappings";
    private final static Logger logger = Logger.getLogger(KubernetesSession.class.getName());
    private Map<String, String> configuration;
    private KubernetesClient client;
    private Map<String, JobTemplate> jobTemplates;
    private Map<String, Job> runningJobs;
    private Map<String, LogWatch> logWatchers;
    private Map<String, OutputAccumulator> jobOutputs;
    private AtomicInteger nodeCounter;
    private List <LogWatch> jobsLog;
    private Map<String, String> pvcMappings;

    public KubernetesSession() {
        this.configuration = KubernetesSession.getConfiguration();
        this.client = KubernetesSession.getClient(this.configuration);
        if (this.client != null) {
            this.pvcMappings = this.parsePvcMappings();
        }
    }

    @Override
    public String serviceName() { return "Kubernetes"; }

    @Override
    public void init(String contact) throws DrmaaException {
        synchronized (this) {
            this.jobTemplates = Collections.synchronizedMap(new HashMap<>());
            this.runningJobs = Collections.synchronizedMap(new HashMap<>());
            this.logWatchers = Collections.synchronizedMap(new HashMap<>());
            this.jobOutputs = Collections.synchronizedMap(new HashMap<>());
            this.nodeCounter = new AtomicInteger(0);

            Map<String, String> newConfiguration = KubernetesSession.getConfiguration();
            if(KubernetesSession.configurationHasChanged(this.configuration, newConfiguration)) {
                this.configuration = newConfiguration;
                this.client = KubernetesSession.getClient(newConfiguration);
                if (this.client != null) {
                    this.pvcMappings = this.parsePvcMappings();
                }
            }
        }
    }

    @Override
    public void exit() throws DrmaaException {
        synchronized (this) {
            checkSession();
            this.jobTemplates.clear();
            this.runningJobs.clear();
            this.logWatchers.clear();
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
        //client.nodes().withLabel("node-role.kubernetes.io/master", "true").resources().collect(Collectors.toList()).get(0).get().getMetadata().getName()
        this.jobTemplates.put(jobTemplate.getJobName(), jobTemplate);
        return jobTemplate;
    }

    @Override
    public void deleteJobTemplate(JobTemplate jt) throws DrmaaException {
        checkSession();
        checkRegistered(jt);
        this.jobTemplates.remove(jt.getJobName());
    }

    @Override
    public int getJobExitCode(String jobId) {
        int exitCode = -1;

        // List the pods with the label selector matching the job
        PodList podList = client.pods().inNamespace(KubernetesSession.namespace)
                .withLabel("job-name", jobId)
                .list();
        for (Pod pod : podList.getItems()) {
            // Iterate through the container statuses to get the exit code
            for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
                if (status.getState().getTerminated() != null) {
                    exitCode = status.getState().getTerminated().getExitCode();
                } else {
                    // Container is not terminated yet
                }
            }
        }
        return exitCode;
    }

    @Override
    public String getJobOutput(String jobId) {
        OutputAccumulator consumer = this.jobOutputs.get(jobId);
        return consumer != null ? consumer.getOutput() : "n/a";
    }

    @Override
    public void cleanupJob(String jobId) {
        this.jobOutputs.remove(jobId);
        this.runningJobs.remove(jobId);
        this.logWatchers.remove(jobId);
    }

    @Override
    public String runJob(JobTemplate jt) throws DrmaaException {
        checkSession();
        checkRegistered(jt);
        if (jt.getRemoteCommand() == null || jt.getRemoteCommand().isEmpty() || jt.getArgs() == null) {
            throw new InvalidJobTemplateException();
        }
        synchronized (this) {
            ContainerUnit containerUnit = null;
            Long jobId = null;
            Long taskId = null;
            Long memConstraint = null;
            NodeDescription node = null;
            final List<String> args = new ArrayList<>();
            ExecutionUnitFormat format = ExecutionUnitFormat.TAO;

            if (jt instanceof JobTemplateExtension) {
                JobTemplateExtension job = (JobTemplateExtension) jt;
                if (job.hasAttribute(DrmaaJobExtensions.CONTAINER_ATTRIBUTE)) {
                    containerUnit = (ContainerUnit) job.getAttribute(DrmaaJobExtensions.CONTAINER_ATTRIBUTE);
                    //args.addAll(ContainerCmdBuilder.buildCommandLineArguments(containerUnit));
                }
                if (job.hasAttribute(DrmaaJobExtensions.MEMORY_REQUIREMENTS_ATTRIBUTE)) {
                    Object value = job.getAttribute(DrmaaJobExtensions.MEMORY_REQUIREMENTS_ATTRIBUTE);
                    memConstraint = value != null ? Long.parseLong(value.toString()) : null;
                }
                if (job.hasAttribute(DrmaaJobExtensions.JOB_ID)) {
                    Object value = job.getAttribute(DrmaaJobExtensions.JOB_ID);
                    jobId = value != null ? Long.parseLong(value.toString()) : null;
                }
                if (job.hasAttribute(DrmaaJobExtensions.TASK_ID)) {
                    Object value = job.getAttribute(DrmaaJobExtensions.TASK_ID);
                    taskId = value != null ? Long.parseLong(value.toString()) : null;
                }

            }
            args.add(jt.getRemoteCommand());
            args.addAll(jt.getArgs());

            final ExecutionUnit unit = new ExecutionUnit(
                    ExecutorType.SSH2,
                    jt.getJobName(),
                    null,
                    null,
                    args,
                    false,
                    SSHMode.EXEC,
                    false,
                    jt.getJobName() + "_" + System.nanoTime(),
                    ExecutionUnitFormat.TAO
            );
            unit.setContainerUnit(containerUnit);

            if (memConstraint != null) {
                unit.setMinMemory(memConstraint);
            }
            //final String jobId = jt.getJobName() + ":" + System.nanoTime();
            final String jobName = "job-id-"+jobId+"-"+taskId;
            Job newJob = execute(jobName, unit);
            this.runningJobs.put(jobName, newJob);

            // TODO: maybe the log should be used only at the end of the job
            /*
            // streaming to System.out
            LogWatch lw = this.client.batch().v1().jobs().inNamespace(namespace).withName(newJob.getMetadata().getName()).watchLog(System.out);
            this.logWatchers.put(jobId, lw);
            // end TODO
             */
            return jobName;
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
        LogWatch lw = this.logWatchers.get(jobId);
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
                if (lw != null) {
                    lw.close();
                }
                logger.info("Logger closed for jobId / name: " + jobId + " / " + job.getMetadata().getName());
                cleanupJob(jobId);
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
        LogWatch lw = this.logWatchers.get(jobId);
        if (job == null) {
            throw new InvalidJobException();
        }
        Pod pod = getPod(job);
        PodStatus status = EnumUtils.getEnumConstantByFriendlyName(PodStatus.class, pod.getStatus().getPhase());
        if (status == null) {
            status = PodStatus.UNKNOWN;
        }
        final int statusId;
        String log;
        switch (status) {
            case PENDING:
                statusId = QUEUED_ACTIVE;
                break;
            case RUNNING:
                statusId = RUNNING;
                log = client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()).withPrettyOutput().getLog();
                this.jobOutputs.computeIfAbsent(jobId, new Function<String, OutputAccumulator>() {
                    @Override
                    public OutputAccumulator apply(String s) {
                        OutputAccumulator accumulator = new OutputAccumulator();
                        accumulator.consume(s);
                        return accumulator;
                    }
                }).consume(log);
                break;
            case SUCCEEDED:
                statusId = DONE;
                if (lw != null) {
                    lw.close();
                }
                logger.finest("Logger closed for jobId / name: " + jobId + " / " + job.getMetadata().getName());
                log = client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()).withPrettyOutput().getLog();
                this.jobOutputs.computeIfAbsent(jobId, new Function<String, OutputAccumulator>() {
                    @Override
                    public OutputAccumulator apply(String s) {
                        OutputAccumulator accumulator = new OutputAccumulator();
                        accumulator.consume(s);
                        return accumulator;
                    }
                }).consume(log);
                logger.fine("Pod LOG: " + log);
                //cleanupJob(jobId);
                break;
            case FAILED:
                statusId = FAILED;
                if (lw != null) {
                    lw.close();
                }
                log = client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()).withPrettyOutput().getLog();
                this.jobOutputs.computeIfAbsent(jobId, new Function<String, OutputAccumulator>() {
                    @Override
                    public OutputAccumulator apply(String s) {
                        OutputAccumulator accumulator = new OutputAccumulator();
                        accumulator.consume(s);
                        return accumulator;
                    }
                }).consume(log);
                logger.fine("Pod LOG: " + log);
                //cleanupJob(jobId);
                break;
            case UNKNOWN:
            default:
                statusId = UNDETERMINED;
                if (lw != null) {
                    lw.close();
                }
                //cleanupJob(jobId);
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
    private Job execute(String jobName, ExecutionUnit unit) {
        Container container = new Container();
        ContainerUnit containerUnit = new ContainerUnit(ContainerType.KUBERNETES);

        try {
            containerUnit.setContainerName(unit.getContainerUnit().getContainerName());
            containerUnit.setContainerRegistry(unit.getContainerUnit().getContainerRegistry());
            unit.getContainerUnit().getVolumeMap().forEach(containerUnit::addVolumeMapping);
            containerUnit.setEnvironmentVariables(unit.getContainerUnit().getEnvironmentVariables());
            containerUnit.setArguments(unit.getContainerUnit().getArguments());

            container.setName(containerUnit.getContainerName());
            container.setImage(containerUnit.getContainerRegistry()+"/"+containerUnit.getContainerName());
            container.setCommand(unit.getArguments());

            logger.info("Pod original cmd: " + container.getCommand().toString());

            ArrayList<String> args = new ArrayList<>();
            for(String s : unit.getArguments()) {
                args.add(StringUtils.strip(s, "\""));
            }
            container.setCommand(args);

            logger.info("Pod cmd: " + container.getCommand().toString());
            logger.info("Pod args: " + container.getArgs().toString());

            Map<String, Volume> pvcVolumeMap = new HashMap<>();

            List<Volume> volumes = null;
            if (containerUnit.getVolumeMap() != null) {
                final List<VolumeMount> mounts = new ArrayList<>();
                volumes = new ArrayList<>();
                int volIdx = 0;
                // TODO: This is for hostPath mount only ! Implement it for s3 as well
                for (Map.Entry<String, String> entry : containerUnit.getVolumeMap().entrySet()) {

                    for (Map.Entry<String, String> pvcEntry : pvcMappings.entrySet()) {
                        if(entry.getKey().startsWith(pvcEntry.getKey())) {

                            Volume volToAdd = pvcVolumeMap.get(pvcEntry.getValue());

                            VolumeMount mount = new VolumeMount();
                            String volName = volToAdd != null ? volToAdd.getName() : "volume-" + volIdx++;
                            mount.setName(volName);
                            mount.setMountPath(StringUtils.stripEnd(entry.getValue(), "/\\"));
                            mount.setSubPath(StringUtils.strip(entry.getKey().substring(pvcEntry.getKey().length()), "/\\"));
                            mounts.add(mount);

                            if(volToAdd == null) {
                                volToAdd = new Volume();
                                //volToAdd.setHostPath(new HostPathVolumeSource(entry.getKey(), "DirectoryOrCreate"));// replace For tests
                                volToAdd.setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder().withClaimName(pvcEntry.getValue()).build());
                                volToAdd.setName(volName);
                                volumes.add(volToAdd);

                                pvcVolumeMap.put(pvcEntry.getValue(), volToAdd);
                            }
                        }
                    }

                }
                container.setVolumeMounts(mounts);
            }

            if (!containerUnit.getEnvironmentVariables().isEmpty()) {
                List envVars = new ArrayList();
                for (Map.Entry<String, String> entry : containerUnit.getEnvironmentVariables().entrySet()) {
                    envVars.add(new EnvVar(entry.getKey(), entry.getValue(), null));
                }
                container.setEnv(envVars);
            }

            ResourceRequirements resourceRequirements = new ResourceRequirements();
            resourceRequirements.setRequests(Map.of(
                    "memory", new Quantity(unit.getMinMemory() + "Mi")
                    // TODO add when available
                    // "cpu", new Quantity(unit.getMinCpu() + "m")
            ));
            container.setResources(resourceRequirements);

            final Job job = new JobBuilder()
                    .withApiVersion("batch/v1")
                    .withNewMetadata()
                    .withName(jobName)
                    //.withLabels(Collections.singletonMap("Label", //put job label here))
                    .endMetadata()
                    .withNewSpec()
                    .withBackoffLimit(0)
                    // by using ttlSecondsAfterFinished, the job will be deleted automatically after it finishes, so the "delete" function may not be called anymore
                    .withTtlSecondsAfterFinished(200)
                    .withNewTemplate()
                    .withNewSpec()
                    //.withNodeSelector(Collections.singletonMap("role-pg", "db"))
                    .addNewContainerLike(container)
                    .endContainer()
                    .withVolumes(volumes)
                    .withRestartPolicy("Never")
                    .endSpec()
                    .endTemplate()
                    .endSpec()
                    .build();

            client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
            return job;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Pod getPod(Job job) {
        // Get All pods created by the job
        PodList podList = client.pods().inNamespace(namespace).withLabel("job-name", job.getMetadata().getName()).list();
        // Wait for pod to complete
        return client.pods().inNamespace(namespace).withName(podList.getItems().get(0).getMetadata().getName()).get();
    }

    private void stopPods(Job job) {
        client.resource(job).inNamespace(namespace).delete();
    }

    static Map<String, String> getConfiguration() {
        Map<String, String> configuration = new HashMap<>();

        String url =  ConfigurationManager.getInstance().getValue(KubernetesSession.masterURL);
        configuration.put(KubernetesSession.masterURL, url);

        String serviceAccountToken = ConfigurationManager.getInstance().getValue(KubernetesSession.token);
        configuration.put(KubernetesSession.token, serviceAccountToken);

        String serviceAccountCertFilepath = ConfigurationManager.getInstance().getValue(KubernetesSession.caCertFile);
        configuration.put(KubernetesSession.caCertFile, serviceAccountCertFilepath);

        String pvcMappingsValue = ConfigurationManager.getInstance().getValue(KubernetesSession.pvcMappingsKey);
        configuration.put(KubernetesSession.pvcMappingsKey, pvcMappingsValue);

        return configuration;
    }

    static boolean configurationHasChanged(Map<String, String> configuration, Map<String, String> newConfiguration) {
        if(configuration != null && newConfiguration != null) {
            return !configuration.equals(newConfiguration);
        } else {
            return configuration != null || newConfiguration != null;
        }
    }

    static boolean configurationHasErrors(Map<String, String> configuration) {
        if (configuration == null) return true;

        boolean hasErrors = false;
        if (configuration.get(KubernetesSession.masterURL) == null) {
            logger.warning("Kubernetes Cluster URL not found in configuration");
            hasErrors = true;
        }
        if (configuration.get(KubernetesSession.token) == null) {
            logger.warning("Kubernetes service account token not found in configuration");
            hasErrors = true;
        }
        if (configuration.get(KubernetesSession.caCertFile) == null || !Files.exists(Paths.get(configuration.get(KubernetesSession.caCertFile)))) {
            logger.warning("Kubernetes service account certificate file not found");
            hasErrors = true;
        }
        if (configuration.get(KubernetesSession.pvcMappingsKey) == null) {
            logger.warning("No PVC mappings found for Kubernetes");
            hasErrors = true;
        }
        return hasErrors;
    }

    static KubernetesClient getClient(Map<String, String> configuration) {
        if(!KubernetesSession.configurationHasErrors(configuration)) {
            try {
                io.fabric8.kubernetes.client.Config builder = new io.fabric8.kubernetes.client.ConfigBuilder()
                        .withMasterUrl(configuration.get(KubernetesSession.masterURL))
                        .withNamespace(configuration.get(KubernetesSession.namespace))
                        .withCaCertFile(configuration.get(KubernetesSession.caCertFile))
                        .withOauthToken(configuration.get(KubernetesSession.token))
                        .build();

                return new KubernetesClientBuilder()
                        .withConfig(builder)
                        .build();
            } catch (Exception e) {
                logger.severe(e.getMessage());
                return null;
            }
        } else {
            return null;
        }
    }

    private Map<String, String> parsePvcMappings() {
        if (this.configuration != null) {
            pvcMappings = new HashMap<>();
            try {
                return JsonMapper.instance().readerFor(pvcMappings.getClass()).readValue(this.configuration.get(KubernetesSession.pvcMappingsKey));
            } catch (JsonProcessingException e) {
                logger.warning("Invalid PVC mappings for Kubernetes");
                return null;
            }
        } else {
            return null;
        }
    }
}