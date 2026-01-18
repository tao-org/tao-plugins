package ro.cs.tao.utils.executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3id.cwl.cwl1_2.*;
import org.w3id.cwl.cwl1_2.utils.RootLoader;
import ro.cs.tao.Tag;
import ro.cs.tao.component.*;
import ro.cs.tao.component.enums.*;
import ro.cs.tao.datasource.param.JavaType;
import ro.cs.tao.docker.Application;
import ro.cs.tao.docker.Container;
import ro.cs.tao.docker.ContainerType;
import ro.cs.tao.docker.ContainerVisibility;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.persistence.*;
import ro.cs.tao.security.SystemPrincipal;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;
import ro.cs.tao.services.interfaces.ComponentService;
import ro.cs.tao.services.interfaces.WorkflowService;
import ro.cs.tao.topology.docker.DockerManager;
import ro.cs.tao.utils.cwl.*;
import ro.cs.tao.utils.cwl.CWLTemplate;
import ro.cs.tao.utils.workflow.WorkflowBuild;
import ro.cs.tao.utils.yaml.CWLYAMLFactory;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ro.cs.tao.utils.cwl.CWLConstants.*;

public class CWLImportExecutor {

    private final CWLTemplate cwlTemplate = new CWLTemplate();
    private final List<CWLInputDependenciesTemplate> cwlInputDependencies = new ArrayList<>();
    private final List<String> createdComponentsHistory = new ArrayList<>();
    private final List<Tag> componentTags;
    private final Map<String, Object> yamlInfo = new LinkedHashMap<>();
    private final ComponentService componentService;
    private final ContainerProvider containerProvider;
    private final TagProvider tagProvider;
    private final WorkflowService workflowService;
    private WorkflowDescriptor workflowDescriptor;
    private final Map<String, List<CWLApplicationTemplate>> applicationListMap = new LinkedHashMap<>();

    private static final Logger logger = Logger.getLogger(CWLImportExecutor.class.getName());

    public CWLImportExecutor() {
        this.componentService = SpringContextBridge.services().getService(ComponentService.class);
        this.containerProvider = SpringContextBridge.services().getService(ContainerProvider.class);
        this.workflowService = SpringContextBridge.services().getService(WorkflowService.class);
        this.tagProvider = SpringContextBridge.services().getService(TagProvider.class);
        this.componentTags = tagProvider.list();
    }

    public CWLImportExecutor(ComponentService componentService, ContainerProvider containerProvider, WorkflowService workflowService){
        this.componentService = componentService;
        this.containerProvider = containerProvider;
        this.tagProvider = SpringContextBridge.services().getService(TagProvider.class);
        this.workflowService = workflowService;
        this.componentTags = tagProvider.list();
    }

    public WorkflowDescriptor parse(List<Path> sources) throws PersistenceException, IOException, IllegalAccessException {
        ObjectMapper mapper = new ObjectMapper(new CWLYAMLFactory());
        // get YAML file
        long numberOfYamlFiles = sources.stream()
                .filter(file -> {
                    String name = file.getFileName().toString();
                    return name.endsWith(".yml") || name.endsWith(".yaml");
                }).count();
        if (numberOfYamlFiles > 1) {
            logger.severe("To many YAML files! Please merge all in one YAML file!");
        } else if (numberOfYamlFiles < 1) {
            logger.warning("YAML file is missing!");
        } else {
            Path yamlFile = sources.stream()
                    .filter(file -> {
                        String name = file.getFileName().toString();
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    }).findFirst().orElse(null);
            if (yamlFile != null) {
                yamlInfo.putAll(mapper.readValue(yamlFile.toFile(), Map.class));
            }
        }

        Map<String, List<String>> roadMap = new HashMap<>();//contains file name and found ids
        // Filter .cwl files
        List<Path> cwlInputStreams = sources.stream()
                .filter(file -> file.getFileName().toString().endsWith(".cwl")).toList();
        // Iterate through cwl files and add information to template
        cwlTemplate.setCwlVersion(CWL_VERSION);
        cwlTemplate.setCwlMainGraphObject(CWL_GRAPH_ID);

        for (Path path : cwlInputStreams) {
            Map<?, ?> rawDoc = mapper.readValue(path.toFile(), Map.class);
            if (rawDoc.get("cwlVersion") != null && !rawDoc.get("cwlVersion").equals(CWL_VERSION)) {
                logger.warning("Expected cwlVersion " + CWL_VERSION + " but found " + rawDoc.get("cwlVersion"));
            }

            List<String> fileIdsList = CWLUtils.getFileIdsList(rawDoc.toString());
            Object graphElements = RootLoader.loadDocument(path);

            if (fileIdsList.isEmpty()) {
                if (graphElements instanceof Collection<?>) {
                    logger.severe("In CWL files that use $graph, every tool and workflow must have an id." +
                            " If you're having trouble importing or running the workflow, a missing id could be the reason.");
                }
                String generatedId = CWLUtils.generateId(path.getFileName().toString());
                fileIdsList.add(generatedId);
            }
            roadMap.put(path.getFileName().toString(), fileIdsList);

            if (graphElements instanceof Collection<?>) {
                cwlTemplate.addAllElements((Collection<?>) graphElements, path.getFileName().toString());
            } else {
                cwlTemplate.addElement(graphElements, path.getFileName().toString());
            }
        }
        if (sources.size() > 1) {
            boolean hasWorkflow = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream().anyMatch(obj -> obj instanceof Workflow);
            if (!hasWorkflow) {
                logger.warning("More than one CWL file with class CommandLineTool with no Workflow!");
            }
            cwlTemplate.replaceRunFilesWithIds(roadMap);
        }
        createNodes();
        return workflowDescriptor;
    }

    /**
     * Creates the main logic responsible for initializing and assembling the core elements of a workflow.
     */
    private void createNodes() throws PersistenceException {
        String mainWfId = getMainWfId();
        if (mainWfId == null || mainWfId.isEmpty()) {
            mainWfId = "new_Workflow";
            logger.warning("No Workflow class or CommandLineTool with id was found! A default name was set");
        } else {
            boolean linkedByWorkflow = existCwlLinks();
            if (!linkedByWorkflow && !yamlInfo.isEmpty()) {
                // modify cwl template to contains link
                updateCwlTemplateWithLinks(mainWfId);
            } else if (!linkedByWorkflow) {
                logger.warning("Workflow nodes are probably linked manually by using YAML file! If the YAML file is missing the nodes will not be linked!");
            }
        }
        createWorkflowNodeHierarchy();
        List<CommandLineTool> commandLineToolList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(g -> g instanceof CommandLineTool).map(g -> (CommandLineTool) g).toList();
        List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(wf -> wf instanceof Workflow).map(wf -> (Workflow) wf).toList();
        boolean skipContainer = false;
        boolean skipCommandLineTool = false;
        for (CommandLineTool commandLineTool : commandLineToolList) {
            List<Object> wfAssociatedInputs = null;
            String wfAssociatedStepId = null;
            List<String> wfAssociatedStepIdList = new ArrayList<>();
            if (workflowList != null && !workflowList.isEmpty()) {
                WorkflowStep wfAssociatedStep = workflowList.stream().flatMap(wf -> wf.getSteps().stream())
                        .filter(step -> ((WorkflowStep) step).getRun().toString().equals(commandLineTool.getId().get()))
                        .map(step -> (WorkflowStep) step).findFirst().orElse(null);
                wfAssociatedStepIdList = workflowList.stream().flatMap(wf -> wf.getSteps().stream())
                        .filter(step -> ((WorkflowStep) step).getRun().toString().equals(commandLineTool.getId().get()))
                        .map(step -> (WorkflowStep) step).map(WorkflowStep::getId).map(Optional::get).toList();
                if (wfAssociatedStep != null) {
                    wfAssociatedInputs = wfAssociatedStep.getIn();
                    wfAssociatedStepId = wfAssociatedStep.getId().get();
                    skipCommandLineTool = false;
                } else {
                    skipCommandLineTool = true;
                }
            }
            if(!skipCommandLineTool) {
                List<CWLApplicationTemplate> applicationList = CWLAppDetector.createApplicationTemplate(commandLineTool, wfAssociatedInputs);
                applicationListMap.put(commandLineTool.getId().get(),applicationList);
                Container dockerContainer = getAssosiatedContainer(commandLineTool, applicationList);
                if (dockerContainer == null) {
                    String errorMessage = "one of the CWL classes";
                    if (commandLineTool.getId().isPresent() && !commandLineTool.getId().get().isEmpty()) {
                        errorMessage = commandLineTool.getId().get();
                    } else if (!applicationList.isEmpty()) {
                        errorMessage = "CWL with applications: " + applicationList.stream().map(CWLApplicationTemplate::getApplicationName).collect(Collectors.joining(", "));
                    }
                    logger.severe("Container unit is null or can not be created, for " + errorMessage + ".Please check that the docker image is available!");
                    skipContainer = true;
                }
                if (!skipContainer) {
                    dockerContainer = WorkflowBuild.addMissingAppInContainer(dockerContainer, applicationList);
                    initializeProcessingComponent(applicationList, dockerContainer, mainWfId, wfAssociatedStepId, wfAssociatedStepIdList);
                }
            }
        }
        adjustProcessingComponent();
        adjustProcessingComponentsCategory();
        if (!skipContainer) {
            createWorkflowDescriptor(mainWfId);
        }
    }

    /**
     * Checks if a processing component exists; if not, creates a new one.
     * If the component is found, any missing sources, targets or parameters
     * will be added to ensure the workflow is configured correctly.
     *
     * @param applicationList a list of applications defined in the current CommandLineTool
     * @param dockerContainer the docker container defined in the current CommandLineTool
     * @param mainWfId the id of the Workflow used to start the CWL execution
     * @param stepId the step id where the applications are found
     */
    private void initializeProcessingComponent(List<CWLApplicationTemplate> applicationList, Container dockerContainer, String mainWfId,
                                               String stepId, List<String> associatedIdList) throws PersistenceException {
        for (CWLApplicationTemplate application : applicationList) {
            String componentLabel = CWLUtils.simplifyApplicationName(application.getApplicationName());
            String componentId = CWLUtils.simplifyApplicationName(application.getApplicationName());
            String suffix = stepId.matches(".+_\\d+$") ? stepId.substring(stepId.lastIndexOf("_")) : "";
            ProcessingComponent processingComponent = findComponent(componentId);
            boolean componentAlreadyExist = true;
            ComponentCategory componentCategory = ComponentCategory.MISC;
            Map<String, CWLParamDescTemplate> paramsToBeAdded = new LinkedHashMap<>();
            if (processingComponent == null) {
                String fileLocation = dockerContainer.getApplications().stream()
                        .filter(app -> app.getName().equals(componentId))
                        .map(Application::getPath).findFirst().orElse(null);
                componentAlreadyExist = false;
                String componentTemplate = createTemplate(application.getApplicationParams(), suffix);
                //creating processing component
                processingComponent = createProcessingComponents(componentId,componentLabel,fileLocation,componentCategory,componentTemplate,dockerContainer);
                createdComponentsHistory.add(componentId);
            }

            //used in the workaround for cases where .csv file is mandatory, but it is not an input
            long numberOfInputs = application.getApplicationParams().entrySet().stream().filter(e -> e.getValue().getParamType().equals(INPUT_PARAM_POSITION)).count();
            // add source application name to cwl dependencies template
            cwlInputDependencies.stream().filter(dp -> associatedIdList.contains(dp.getSourceNodeName())).forEach(dp -> dp.setSourceApplicationName(componentId));
            // add target application name to cwl dependencies template
            cwlInputDependencies.stream().filter(dp -> associatedIdList.contains(dp.getTargetNodeName())).forEach(dp -> dp.setTargetApplicationName(componentId));
            for (Map.Entry<String, CWLParamDescTemplate> entry : application.getApplicationParams().entrySet()) {
                boolean foundInTemplate = foundInTemplate(processingComponent.getTemplateContents(), entry.getValue());
                String entryKey = CWLUtils.removeNumericSuffix(entry.getKey());
                String entryFoundName = CWLUtils.removeStartDashes(entry.getValue().getParamName());
                String parameterKey = entry.getKey();
                String paramType = entry.getValue().getParamType();
                DataDescriptor dataDescriptor = new DataDescriptor(getDataFormatType(mainWfId, stepId, parameterKey, entry.getValue().getFormatType(), paramType));
                //workaround for cases where .csv file is mandatory, but it is not an input
                boolean isLinked = checkIfInputIsLinked(entry.getKey());
                if (!isLinked && !entry.getValue().getParamName().matches("processor(.\\w+)*")
                        && !entry.getValue().getParamName().matches("^input(_\\w+)*") && numberOfInputs > 1
                        && entry.getValue().getParamType().equals(INPUT_PARAM_POSITION)) {
                    Object entryValue = findParamValue(mainWfId, stepId, parameterKey, paramType);
                    String extension = null;
                    if (entryValue != null) {
                        extension = CWLUtils.getFileExtension(entryValue);
                    }
                    if ((extension == null || PARAM_FILE_EXTENSION.contains(extension))) {
                        entry.getValue().setParamType(PARAMETER_PARAM_POSITION);
                    }
                }
                // end of workaround
                Object defaultValue = entry.getValue().getDefaultValue();
                //boolean descriptorAlreadyExist;
                if (entry.getValue().getParamType().equals(INPUT_PARAM_POSITION)) {
                    SourceDescriptor sourceDescriptor = processingComponent.getSources().stream()
                            .filter(pc -> pc.getName().equals(entryKey)).findFirst().orElse(null);
                    if (sourceDescriptor == null) {
                        sourceDescriptor = processingComponent.getSources().stream()
                                .filter(pc -> pc.getName().equals(entryFoundName)).findFirst().orElse(null);
                    }
                    if (sourceDescriptor == null) {
                        sourceDescriptor = createSourceDescriptor(componentId,entryFoundName,dataDescriptor,defaultValue,entry.getValue().getFormatType());
                        if (!componentAlreadyExist) {
                            processingComponent.addSource(sourceDescriptor);
                        } else {
                            if (!foundInTemplate) {
                                String foundParam = getParamFromPositionInTemplate(processingComponent.getTemplateContents(), entry.getValue());
                                DataDescriptor foundDataDescriptor = processingComponent.getSources().stream()
                                        .filter(pc -> pc.getName().equals(foundParam)).map(SourceDescriptor::getDataDescriptor).findFirst().orElse(null);
                                if (foundDataDescriptor != null && !dataDescriptor.getFormatType().equals(foundDataDescriptor.getFormatType())) {
                                    paramsToBeAdded.put(entry.getKey(), entry.getValue());
                                    processingComponent.addSource(sourceDescriptor);
                                } else {
                                    updateInputReference(foundParam, entry.getKey());
                                }
                            }
                        }
                    } else if (sourceDescriptor.getDataDescriptor().getFormatType().equals(DataFormat.OTHER)) {
                        sourceDescriptor.setDataDescriptor(dataDescriptor);
                    }
                }
                if (entry.getValue().getParamType().equals(OUTPUT_PARAM_POSITION)) {
                    TargetDescriptor targetDescriptor = processingComponent.getTargets().stream()
                            .filter(pc -> pc.getName().equals(entryKey)).findFirst().orElse(null);
                    if (targetDescriptor == null ) {
                        targetDescriptor = processingComponent.getTargets().stream()
                                .filter(pc -> pc.getName().equals(entryFoundName)).findFirst().orElse(null);
                    }
                    if (targetDescriptor == null) {
                        targetDescriptor = createTargetDescriptor(componentId,entryFoundName,dataDescriptor,mainWfId,stepId,entry);
                        if (!componentAlreadyExist) {
                            processingComponent.addTarget(targetDescriptor);
                        } else {
                            if (!foundInTemplate) {
                                String foundParam = getParamFromPositionInTemplate(processingComponent.getTemplateContents(), entry.getValue());
                                DataDescriptor foundDataDescriptor = processingComponent.getTargets().stream()
                                        .filter(pc -> pc.getName().equals(foundParam)).map(TargetDescriptor::getDataDescriptor).findFirst().orElse(null);
                                if (foundDataDescriptor != null && !dataDescriptor.getFormatType().equals(foundDataDescriptor.getFormatType())) {
                                    paramsToBeAdded.put(entry.getKey(), entry.getValue());
                                    processingComponent.addTarget(targetDescriptor);
                                } else {
                                    updateOutputReference(foundParam, entry.getKey());
                                }
                            }
                        }
                    } else if (targetDescriptor.getDataDescriptor().getFormatType().equals(DataFormat.OTHER)) {
                        targetDescriptor.setDataDescriptor(dataDescriptor);
                    }
                }
                if (entry.getValue().getParamType().equals(PARAMETER_PARAM_POSITION)) {
                    boolean descriptorAlreadyExist = processingComponent.getParameterDescriptors().stream()
                            .anyMatch(pc -> pc.getName().equals(entryKey));
                    if (!descriptorAlreadyExist) {
                        descriptorAlreadyExist = processingComponent.getParameterDescriptors().stream()
                                .anyMatch(pc -> pc.getName().equals(entryFoundName));
                    }
                    if (!descriptorAlreadyExist) {
                        ParameterDescriptor parameterDescriptor = createParameterDescriptor(entryKey,entryFoundName,mainWfId,entry);
                        if (!componentAlreadyExist) {
                            processingComponent.addParameter(parameterDescriptor);
                        } else {
                            if (!foundInTemplate) {
                                paramsToBeAdded.put(entry.getKey(), entry.getValue());
                                processingComponent.addParameter(parameterDescriptor);
                            }
                        }
                        processingComponent.addParameter(parameterDescriptor);
                        paramsToBeAdded.put(entry.getKey(), entry.getValue());
                    }
                }
            }
            //condition used just to warn about missing sources and targets
            if (processingComponent.getSources().isEmpty() || processingComponent.getSources().isEmpty()) {
                String message = processingComponent.getSources().isEmpty() ? "sources " : "";
                if (processingComponent.getTargets().isEmpty()) {
                    message = message.isEmpty() ? "targets" : "and targets";
                }
                logger.warning(processingComponent.getId() + " component does not have " + message + "!");
            }

            if (!paramsToBeAdded.isEmpty()) {
                String componentTemplate = updateTemplate(processingComponent.getTemplateContents(), paramsToBeAdded, suffix);
                processingComponent.setTemplateContents(componentTemplate);
            }

            if (componentAlreadyExist) {
                componentService.update(processingComponent);
            } else {
                componentService.save(processingComponent);
            }
        }
    }

    /**
     * Creates a new processing component.
     *
     * @param componentId the processing component id
     * @param componentLabel the processing component label
     * @param fileLocation the location for a processing component
     * @param componentCategory the processing component category
     * @param componentTemplate the generated template
     * @param dockerContainer the docker container defined in the current CommandLineTool
     * @return a new processing component
     */
    private ProcessingComponent createProcessingComponents(String componentId, String componentLabel, String fileLocation, ComponentCategory componentCategory,
                                                           String componentTemplate, Container dockerContainer) throws PersistenceException {
        ProcessingComponent processingComponent = new ProcessingComponent();
        processingComponent.setId(componentId);
        processingComponent.setContainerId(dockerContainer.getId());
        processingComponent.setLabel(componentLabel);
        processingComponent.setFileLocation(fileLocation);
        processingComponent.setComponentType(ProcessingComponentType.EXECUTABLE);
        processingComponent.setVisibility(ProcessingComponentVisibility.SYSTEM);
        processingComponent.setVersion(dockerContainer.getTag());
        processingComponent.setOwner(SystemPrincipal.instance().getName());
        processingComponent.setTags(List.of(getOrCreateTag(dockerContainer.getName()).getText()));
        processingComponent.setTemplateContents(componentTemplate);
        processingComponent.setDescription("");
        processingComponent.setAuthors("");
        processingComponent.setCopyright("");
        processingComponent.setCategory(componentCategory);
        // default working directory
        processingComponent.setWorkingDirectory(".");
        return processingComponent;
    }

    /**
     * Creates a new source descriptor.
     *
     * @param componentId the associated processing component id
     * @param name the source name
     * @param dataDescriptor the data descriptor found in the CWL
     * @param defaultValue the source default value, found in the CWL
     * @param sourceFormatType the format type found in the CWL
     * @return a new source descriptor
     */
    private SourceDescriptor createSourceDescriptor(String componentId, String name, DataDescriptor dataDescriptor,
                                                    Object defaultValue, String sourceFormatType) {
        SourceDescriptor sourceDescriptor = new SourceDescriptor();
        sourceDescriptor.setParentId(componentId);
        sourceDescriptor.setName(name);
        sourceDescriptor.setCardinality(1);
        sourceDescriptor.setDataDescriptor(dataDescriptor);
        if (defaultValue instanceof Collection<?> || sourceFormatType.contains("[]")) {
            sourceDescriptor.setCardinality(0);
        }
        return sourceDescriptor;
    }

    /**
     * Creates a new target descriptor.
     *
     * @param componentId the associated processing component id
     * @param name the target name
     * @param dataDescriptor the data descriptor found in the CWL
     * @param mainWfId the id of the Workflow used to start the CWL execution
     * @param stepId the step id where the applications are found
     * @param application the component associated application
     * @return a new target descriptor
     */
    private TargetDescriptor createTargetDescriptor(String componentId, String name, DataDescriptor dataDescriptor, String mainWfId,
                                                    String stepId, Map.Entry<String, CWLParamDescTemplate> application) {
        TargetDescriptor targetDescriptor = new TargetDescriptor();
        targetDescriptor.setParentId(componentId);
        targetDescriptor.setName(name);
        if (!yamlInfo.isEmpty()) {
            String location = "";
            if (yamlInfo.get(application.getKey()) instanceof Collection<?>) {
                logger.info("Output " + application.getKey() + " is defined as an array, but array outputs are not currently processed to resolve the target location.");
            } else {
                String entryIdInYml = findAncestorId(mainWfId, stepId, application.getKey(), application.getValue().getParamType());
                if (entryIdInYml != null) {
                    location = yamlInfo.get(entryIdInYml).toString();
                }
            }
            if (location.isEmpty() && application.getValue().getDefaultValue() != null) {
                location = application.getValue().getDefaultValue().toString();
            } else if (location.isEmpty()) {//generate a dummy output file
                location = application.getKey(); // I do not know the file extension
            }
            dataDescriptor.setLocation(location);
        }
        targetDescriptor.setDataDescriptor(dataDescriptor);
        return targetDescriptor;
    }

    /**
     * Creates a new parameter descriptor.
     *
     * @param parameterKey the parameter key
     * @param parameterName the parameter name
     * @param mainWfId the id of the Workflow used to start the CWL execution
     * @param application the component associated application
     * @return a new parameter descriptor
     */
    private ParameterDescriptor createParameterDescriptor(String parameterKey, String parameterName, String mainWfId,
                                                          Map.Entry<String, CWLParamDescTemplate> application) {
        ParameterDescriptor parameterDescriptor = new ParameterDescriptor();
        parameterDescriptor.setDataType(JavaType.fromFriendlyName(application.getValue().getFormatType()).value());
        parameterDescriptor.setName(parameterKey);
        parameterDescriptor.setId(parameterKey);
        parameterDescriptor.setLabel(parameterName);
        parameterDescriptor.setType(ParameterType.REGULAR);
        parameterDescriptor.setNotNull(application.getValue().isMandatory());
        String description = getDescForParam(application.getKey(), mainWfId);
        if (description != null) {
            parameterDescriptor.setDescription(description);
        }
        return parameterDescriptor;
    }

    /**
     * Creates a processing component template based on the provided parameters.
     *
     * @param params a set of processing component parameters used to build the template
     * @return the template as a string
     */
    private String createTemplate(Map<String, CWLParamDescTemplate> params, String suffix) {
        return composeTemplate(new LinkedList<>(), params, suffix);
    }

    /**
     * Updates a processing component template based on the provided parameters.
     *
     * @param componentTemplate already existing component template
     * @param params a set of processing component parameters used to build the template
     * @return the template as a string
     */
    private String updateTemplate(String componentTemplate, Map<String, CWLParamDescTemplate> params, String suffix) {
        LinkedList<String> paramTemplateList = splitTemplate(componentTemplate);
        return composeTemplate(paramTemplateList, params, suffix);
    }

    /**
     * Compose the template content. Is used in update and create methods.
     *
     * @param paramTemplateList list with already existing parameters in template (if it comes from update)
     * @param params a set of processing component parameters used to build the template
     * @return the template as a string
     */
    private String composeTemplate(LinkedList<String> paramTemplateList, Map<String, CWLParamDescTemplate> params, String suffix) {
        String firstDeclaredParamPrefix = params.values().stream()
                .map(CWLParamDescTemplate::getParamPrefix)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        String declaredParamPrefix = CWLUtils.extractDashPrefix(firstDeclaredParamPrefix);
        Map<String, CWLParamDescTemplate> paramWithPositionList = params.entrySet().stream()
                .filter(p -> p.getValue().getParamPosition() > 0)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
        Map<String, CWLParamDescTemplate> paramWithoutPositionList = params.entrySet().stream()
                .filter(p -> p.getValue().getParamPosition() < 1)
                .sorted(Comparator.comparingInt(e -> e.getValue().getParamPosition()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
        for (Map.Entry<String, CWLParamDescTemplate> paramEntry: paramWithoutPositionList.entrySet()) {
            String paramName = paramEntry.getValue().getParamName();
            if (paramName.contains(suffix)) {
                int suffixIdx = paramName.indexOf(suffix);
                paramName = paramName.substring(0, suffixIdx);
            }
            String param = paramName;
            String paramVal = null;
            if (paramEntry.getValue().getParamPrefix() != null) {
                param = paramEntry.getValue().getParamPrefix();
                paramVal = paramName.startsWith("-") ? CWLUtils.removeStartDashes(paramName) : paramName;
            } else if (paramEntry.getValue().getParamType().equals(OUTPUT_PARAM_POSITION)) {
                paramVal = declaredParamPrefix + CWLUtils.removeStartDashes(param);
                if (!declaredParamPrefix.isEmpty()) {
                    param = declaredParamPrefix + param;
                }
            }
            String paramTemplate;
            if (param.contains(".")) {
                paramTemplate = paramVal != null ? param + " $" + paramVal.replaceAll("-", "_") : "$" + param;
            } else {
                paramTemplate = paramVal != null ? param + "\n$" + paramVal.replaceAll("-", "_") : "$" + param;
            }
            if (!paramTemplateList.contains(paramTemplate)) {
                if (paramEntry.getValue().getParamPosition() < 0) {
                    paramTemplateList.addFirst(paramTemplate);
                } else {
                    paramTemplateList.add(paramTemplate);
                }
            }
        }

        for (Map.Entry<String, CWLParamDescTemplate> paramEntry: paramWithPositionList.entrySet()) {
            String paramName = paramEntry.getValue().getParamName();
            if (paramName.contains(suffix)) {
                int suffixIdx = paramName.indexOf(suffix);
                paramName = paramName.substring(0, suffixIdx);
            }
            String param = paramName;
            String paramTemplate = "$" + param;
            if (paramEntry.getValue().getParamPrefix() != null) {
                param = paramEntry.getValue().getParamPrefix();
                String paramVal = paramName.startsWith("-") ? CWLUtils.removeStartDashes(paramName) : paramName;
                paramVal = paramVal.replaceAll("-", "_");
                if (param.contains(".")) {
                    paramTemplate = param + " $" + paramVal;
                } else {
                    paramTemplate = param + "\n$" + paramVal;
                }
            }
            if (!paramTemplateList.contains(paramTemplate)) {
                if (paramEntry.getValue().getParamPosition() > 0) {
                    paramTemplateList.add(paramEntry.getValue().getParamPosition() - 1, paramTemplate);
                } else {
                    paramTemplateList.add(paramTemplate);
                }
            }
        }
        return String.join("\n", paramTemplateList);
    }

    /**
     * Checks if a parameter was already added in the processing component template.
     *
     * @param componentTemplate the already existing template
     * @param param the parameter to be searched
     * @return true of false
     */
    private boolean foundInTemplate(String componentTemplate, CWLParamDescTemplate param) {
        if (param.getParamPosition() > 1) {
            LinkedList<String> template = splitTemplate(componentTemplate);
            String paramOnPosition = template.get(param.getParamPosition() - 1);
            return paramOnPosition.equals("$" + param.getParamName()) || componentTemplate.contains("$" + param.getParamName());
        } else {
            String paramName = param.getParamName();
            String paramVal = paramName.startsWith("-") ? CWLUtils.removeStartDashes(paramName) : paramName;
            paramVal = paramVal.replaceAll("-", "_");
            String paramTemplate ;
            if (paramName.contains(".")) {
                paramTemplate = paramName + " $" + paramVal;
            } else {
                paramTemplate = paramName + "\n$" + paramVal;
            }
            return componentTemplate.contains(paramTemplate);
        }
    }

    /**
     * Splits the template in LinkedList by the following pattern: -input1 &input1 is on one position and $input2 on another
     * @param componentTemplate the already existing template
     * @return the new list
     */
    private LinkedList<String> splitTemplate(String componentTemplate) {
        LinkedList<String> template = Arrays.stream(componentTemplate.split("\n")).collect(Collectors.toCollection(LinkedList::new));
        LinkedList<String> newTemplate = new LinkedList<>();
        for (int idx = 0; idx < template.size(); idx++) {
            String value = template.get(idx).startsWith("-") ? CWLUtils.removeStartDashes(template.get(idx)) : template.get(idx);
            if (idx < template.size() - 1 && template.get(idx + 1).equals("$" + value)) {
                newTemplate.add(template.get(idx) + "\n" + template.get(idx + 1));
                idx++;
            } else {
                newTemplate.add(template.get(idx));
            }
        }
        return newTemplate;
    }

    /**
     * Updated the input id in CWL workflow, when it was found with another id in a CWL.
     *
     * @param componentInputId the id already defined for the parameter of a component.
     * @param currentId the new id assigned for the same parameter.
     */
    private void updateInputReference(String componentInputId, String currentId) {
        List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(g -> g instanceof Workflow).map(g -> (Workflow) g).toList();
        if (!workflowList.isEmpty()) {
            for (Workflow workflow : workflowList) {
                for (Object step : workflow.getSteps()) {
                    for (Object in : ((WorkflowStep) step).getIn()) {
                        if (((WorkflowStepInput)in).getId().get().equals(currentId)) {
                            ((WorkflowStepInput)in).setId(Optional.of(componentInputId));
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Updated the output id in CWL workflow, when it was found with another id in a CWL.
     *
     * @param componentInputId the id already defined for the parameter of a component.
     * @param currentId the new id assigned for the same parameter.
     */
    private void updateOutputReference(String componentInputId, String currentId) {
        List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(g -> g instanceof Workflow).map(g -> (Workflow) g).toList();
        if (!workflowList.isEmpty()) {
            for (Workflow workflow : workflowList) {
                for (Object step : workflow.getSteps()) {
                    for (Object in : ((WorkflowStep) step).getIn()) {
                        if (((WorkflowStepInput)in).getSource().toString().contains("/" + currentId)) {
                            String source = ((WorkflowStepInput)in).getSource().toString();
                            source = source.replaceAll("\\b" + Pattern.quote(currentId) + "\\b", componentInputId);
                            ((WorkflowStepInput)in).setSource(source);
                        }
                    }
                    for (Object out : ((WorkflowStep) step).getOut()) {
                        if (out instanceof WorkflowStepOutput && ((WorkflowStepOutput)out).getId().get().equals(currentId)) {
                            ((WorkflowStepOutput)out).setId(Optional.of(componentInputId));
                            updateOutputReferenceInCommandLine(((WorkflowStep) step).getRun().toString(), componentInputId, currentId);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Updated the output id in CWL command line tool, when it was found with another id in a CWL.
     *
     * @param commandLineToolId the id of a command line tool.
     * @param componentInputId the id already defined for the parameter of a component.
     * @param currentId the new id assigned for the same parameter.
     */
    private void updateOutputReferenceInCommandLine(String commandLineToolId, String componentInputId, String currentId) {
        Object commandLineTool = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, commandLineToolId);
        if (commandLineTool instanceof CommandLineTool) {
            for (Object out : ((CommandLineTool) commandLineTool).getOutputs()) {
                if (((CommandOutputParameter) out).getId().get().equals(currentId)) {
                    ((CommandOutputParameter) out).setId(Optional.of(componentInputId));
                    break;
                }
            }
        }
    }

    /**
     * Gets the parameter found on the same position in the template, only if the parameter does not match "param $param".
     *
     * @param componentTemplate the already existing template.
     * @param param the parameter to be searched.
     * @return the already existing parameter or null.
     */
    private String getParamFromPositionInTemplate(String componentTemplate, CWLParamDescTemplate param) {
        if (param.getParamPosition() > 0) {
            LinkedList<String> template = splitTemplate(componentTemplate);
            String paramOnPosition = template.get(param.getParamPosition() - 1);
            if (!paramOnPosition.equals("$" + param.getParamName())) {
                return paramOnPosition.replaceAll("\\$", "");
            }
        }
        return null;
    }

    /**
     * Creates the workflow descriptor, builds the workflow nodes map and hierarchy,
     * links the workflow nodes and assigns their positions on the canvas.
     *
     * @param mainWfId the id of the Workflow used to start the CWL execution
     */
    private void createWorkflowDescriptor(String mainWfId) throws PersistenceException {
        boolean wfAlreadyExist = workflowService.getDescriptor(mainWfId) != null;
        workflowDescriptor = WorkflowBuild.createDescriptor(mainWfId, wfAlreadyExist);
        createWorkflowNodesMap(mainWfId);
        if (!cwlInputDependencies.isEmpty()) {
            linkWorkflowNodes();
        }
        WorkflowBuild.updateNodeNames(workflowDescriptor);
        WorkflowBuild.placeWorkflowNodes(workflowDescriptor);
    }

    /**
     * Creates the workflow node hierarchy by establishing which nodes are linked to each other
     * and adds this structure to a dependency template.
     */
    private void createWorkflowNodeHierarchy() {
        List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(g -> g instanceof Workflow).map(g -> (Workflow) g).toList();
        if (!workflowList.isEmpty()) {
            for (Workflow workflow: workflowList) {
                for (Object step : workflow.getSteps()) {
                    for (Object in : ((WorkflowStep) step).getIn()) {
                        if (((WorkflowStepInput) in).getSource() != null && ((WorkflowStepInput) in).getSource().toString().contains("/")) {
                            ArrayList<String> foundSourceList = CWLUtils.findSourceValues(((WorkflowStepInput) in).getSource());
                            for (String foundSource : foundSourceList) {
                                String[] parts = foundSource.split("/");
                                if (parts.length == 2) {
                                    String sourceId = CWLUtils.findAssociatedRun(parts[0], workflowList);
                                    String sourceNodeName = CWLUtils.findAssociatedStepId(parts[0], workflowList);
                                    addToDependenciesTemplate(sourceId, sourceNodeName, parts[1], ((WorkflowStep) step).getRun().toString(), ((WorkflowStep) step).getId().get(), ((WorkflowStepInput) in).getId().get());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Recursively traverses the CWL workflow structure to build a map of workflow nodes.
     *
     * In case the id corresponds to a Workflow, it recursively processes each step,
     * delegating to this method again for nested workflows or calling createWorkflowNode
     * for CommandLineTool steps.
     * In case the id corresponds to a CommandLineTool or no workflow is found, the method
     * processes all CommandLineTool objects in the CWL main graph.
     *
     * @param id the id of the Workflow or CommandLineTool used to create the workflow node
     */
    private void createWorkflowNodesMap(String id) throws PersistenceException {
        Object workflow = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, id);
        if (workflow instanceof Workflow && workflow != null) {
            for (Object step : ((Workflow) workflow).getSteps()) {
                String nextRun = ((WorkflowStep) step).getRun().toString();
                Object object = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, nextRun);
                if (object instanceof Workflow && ((Workflow) object).getId().isPresent()) {
                    createWorkflowNodesMap(((Workflow) object).getId().get());
                } else if (object instanceof CommandLineTool && ((CommandLineTool) object).getId().isPresent()) {
                    createWorkflowNode(((CommandLineTool) object).getId().get(), ((WorkflowStep) step).getId().get());
                }
            }
        } else {
            List<Object> graphObjects = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID);
            for (Object object : graphObjects) {
                if (object instanceof CommandLineTool) {
                    if (((CommandLineTool) object).getId().isPresent()) {
                        createWorkflowNode(((CommandLineTool) object).getId().get());
                    } else {
                        createWorkflowNode(null);
                    }
                }
            }
        }
    }

    /**
     * Creates a workflow node from a CommandLineTool and adds it to the workflow descriptor.
     *
     * @param nodeId the id of the CommandLineTool used to create the workflow node
     */
    private void createWorkflowNode(String nodeId) throws PersistenceException {
        createWorkflowNode(nodeId, null);
    }

    /**
     * Creates a workflow node from a CommandLineTool and adds it to the workflow descriptor.
     *
     * This method performs the following steps:
     * 1. Generates a processing component template from the CommandLineTool.
     * 2. Finds the corresponding component from the database.
     * 3. Creates a workflow node descriptor from the component and adds it to the workflow.
     * 4. Resolves input/output dependencies and links the node accordingly.
     *
     * @param nodeId the id of the CommandLineTool used to create the workflow node
     */
    private void createWorkflowNode(String nodeId, String stepId) throws PersistenceException {
        CommandLineTool commandLineTool = (CommandLineTool) cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, nodeId);
        List<CWLApplicationTemplate> applicationList = applicationListMap.get(commandLineTool.getId().get());
        if (applicationList.size() > 1) {
            throw new RuntimeException("To many arguments for bash -c for CommandLineTool "
                    + (commandLineTool.getId().isPresent() ? commandLineTool.getId().get() : ""));
        } else {
            String componentId = CWLUtils.simplifyApplicationName(applicationList.getFirst().getApplicationName());
            Map<String, CWLParamDescTemplate> params = applicationList.getFirst().getApplicationParams();
            TaoComponent component = findComponent(componentId);
            if (component == null && componentId.contains("_")) {
                componentId = componentId.substring(componentId.lastIndexOf("_") + 1);
                component = componentService.findById(componentId);
            }
            if (component == null) {
                throw new RuntimeException("Something went wrong. Unable to find component for " + componentId + ".Please check the cwl workflow!");
            }
            WorkflowNodeDescriptor node = WorkflowBuild.createNodeFromComponent(component, stepId, workflowDescriptor);
            workflowDescriptor.addNode(node);
            List<CWLInputDependenciesTemplate> sourceDependency = cwlInputDependencies.stream()
                    .filter(c -> c.getCommandLineToolSourceId().equals(nodeId)
                            && c.getSourceNodeName().equals(node.getName())).toList();
            if (!sourceDependency.isEmpty()) {
                for (CWLInputDependenciesTemplate source : sourceDependency) {
                    source.setSourceWorkflowNodeId(node.getId());
                    source.setSourceName(params.get(source.getSourceName()).getParamName());
                }
            }
            List<CWLInputDependenciesTemplate> targetDependency = cwlInputDependencies.stream()
                    .filter(c -> c.getCommandLineToolTargetId().equals(nodeId)
                            && c.getTargetNodeName().equals(node.getName())).toList();
            if (!targetDependency.isEmpty()) {
                for (CWLInputDependenciesTemplate target : targetDependency) {
                    target.setTargetWorkflowNodeId(node.getId());
                    target.setTargetName(params.get(target.getTargetName()).getParamName());
                }
            }
        }
    }

    /**
     * Retrieves the main workflow from a CWL definition.
     *
     * In case the CWL document contains one or more workflows, the main workflow is returned.
     * In case no workflows are present and the CWL contains only CommandLineTool elements,
     * the first tool with a defined id is returned as the main workflow. This ensures that downstream processing is not impacted.
     *
     * @return the id of the Workflow used to start the CWL execution
     */
    private String getMainWfId() {
        List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(obj -> obj instanceof Workflow).map(obj -> (Workflow) obj).toList();
        if (workflowList.isEmpty()) {
            return cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                    .filter(obj -> obj instanceof CommandLineTool && ((CommandLineTool) obj).getId().isPresent())
                    .map(obj -> ((CommandLineTool) obj).getId().get())
                    .findFirst().orElse(null);
        }
        List<String> workflowRunList = new ArrayList<>(workflowList.stream().flatMap(workflow -> workflow.getSteps().stream())
                .map(s -> (WorkflowStep) s).map(WorkflowStep::getRun)
                .filter(Objects::nonNull).map(Object::toString).toList());
        if (!workflowRunList.isEmpty()) {
            return workflowList.stream()
                    .filter(wf -> wf.getId().isPresent() && !workflowRunList.contains(wf.getId().get()))
                    .map(wf -> wf.getId().get()).findFirst().orElse(null);
        }
        return null;
    }

    /**
     * Searches for the label (description) of a parameter within a CommandLineTool or Workflow
     *
     * @param paramId the parameter identifier
     * @param mainWfId the id of the Workflow used to start the CWL execution
     * @return the parameter label (description) if found or null
     */
    private String getDescForParam(String paramId, String mainWfId) {
        Object mainWf = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, mainWfId);
        if (mainWf instanceof Workflow) {
            return ((Workflow) mainWf).getInputs().stream()
                    .map(in -> (WorkflowInputParameter) in)
                    .filter(in -> in.getId().isPresent() && in.getId().get().equals(paramId))
                    .map(WorkflowInputParameter::getLabel).filter(Objects::nonNull).filter(Optional::isPresent)
                    .map(Optional::get).findFirst().orElse(null);
        }
        if (mainWf instanceof CommandLineTool) {
            return ((CommandLineTool) mainWf).getInputs().stream()
                    .map(in -> (CommandInputParameter) in)
                    .filter(in -> in.getId().isPresent() && in.getId().get().equals(paramId))
                    .map(CommandInputParameter::getLabel).filter(Objects::nonNull).filter(Optional::isPresent)
                    .map(Optional::get).findFirst().orElse(null);
        }
        return null;
    }

    /**
     * Gets the DataFormat type for a given parameter within a CWL workflow.
     *
     * In case the parameter is a source or target, the method attempts to find its ancestor id
     * and infer the format type from its file extension.
     * In case a known format type is identified, overrides the provided formatType.
     *
     * @param mainWfId the id of the Workflow used to start the CWL execution
     * @param parameterKey the identifier of the parameter
     * @param formatType the type input, output or parameter
     * @param paramType the input type for the application
     * @return the DataFormat for the specified parameter
     */
    private DataFormat getDataFormatType(String mainWfId, String stepId, String parameterKey, String formatType, String paramType) {
        final DataFormat retVal;
        if (!paramType.equals(PARAMETER_PARAM_POSITION)) {
            String foundFormatType;
            Object foundValue = findParamValue(mainWfId, stepId, parameterKey, paramType);
            if (foundValue != null) {
                foundFormatType = getFormatTypeFromExtension(foundValue);
                if (!foundFormatType.equals(UNKNOWN_DATA_FORMAT_TYPE)) {
                    formatType = foundFormatType;
                }
            }
        }
        switch (formatType) {
            case "Raster":
                retVal = DataFormat.RASTER;
                break;
            case "Vector":
                retVal = DataFormat.VECTOR;
                break;
            case "Any":
                retVal = DataFormat.ANY;
                break;
            case "Directory": case "Directory[]":
            case "File": case "File[]":
            default:
                retVal = DataFormat.OTHER;
                break;
        }
        return retVal;
    }

    /**
     * Gets the value of a parameter from YAML file.
     *
     * @param inputYmlId the parameter identifier for the YAML file
     * @return the parameter value or null
     */
    private Object getYamlValue(String inputYmlId) {
        if (!yamlInfo.isEmpty()) {
            return yamlInfo.get(inputYmlId);
        }
        return null;
    }

    /**
     * Gets the parameter format type based on file extension.
     *
     * @param yamlObj the parameter value used to extract the format type
     * @return the parameter format type as string
     */
    private String getFormatTypeFromExtension(Object yamlObj) {
        if (yamlObj instanceof Collection<?>) {
            Set<String> foundTypes = new LinkedHashSet<>();
            for (Object value : ((Collection<?>) yamlObj).toArray()) {
                if (value instanceof Map<?, ?> mapObj) {
                    if (mapObj.get("path") instanceof String pathValue) {
                        String fileExtension = CWLUtils.getFileExtension(pathValue);
                        foundTypes.add(getTypeFromExtension(fileExtension));
                    }
                }
            }
            if (foundTypes.size() == 1) {
                return foundTypes.iterator().next();
            }
            return UNKNOWN_DATA_FORMAT_TYPE;
        } if (yamlObj instanceof Map<?, ?> mapObj) {
            if (mapObj.get("path") instanceof String pathValue) {
                String fileExtension = CWLUtils.getFileExtension(pathValue);
                return getTypeFromExtension(fileExtension);
            }
        } else {
            String fileExtension = CWLUtils.getFileExtension(yamlObj.toString());
            return getTypeFromExtension(fileExtension);
        }
        return UNKNOWN_DATA_FORMAT_TYPE;
    }

    /**
     * Gets the format type based on file extension.
     *
     * @param ext the file extension
     * @return the type (Raster, Vector or Unknown) as a string
     */
    private String getTypeFromExtension(String ext) {
        for (Map.Entry<String, List<String>> entry : DATA_FORMAT_TYPE.entrySet()) {
            if (entry.getValue().contains(ext)) {
                return entry.getKey();
            }
        }
        return UNKNOWN_DATA_FORMAT_TYPE;
    }

    /**
     * Retrieves a Tag object from the existing list based on the given text.
     * If a tag with the specified text does not exist a new one is created.
     *
     * @param tagText the text of the tag to retrieve or create
     * @return The existing or new created tag
     */
    private Tag getOrCreateTag(String tagText) throws PersistenceException {
        Tag tag = componentTags.stream().filter((t) -> t.getText().equalsIgnoreCase(tagText)).findFirst().orElse(null);
        if (tag == null) {
            tag = tagProvider.save(new Tag(TagType.COMPONENT, tagText));
            componentTags.add(tag);
        }
        return tag;
    }

    /**
     * Returns an existing Docker container from the database if one is already defined,
     * or creates and saves a new container based on the provided CommandLineTool and application list.
     *
     * @param commandLineTool the CWL CommandLineTool element used to find the docker image
     * @param applicationList the list of applications used in the CWL workflow
     * @return the found or created Container
     */
    private Container getAssosiatedContainer(CommandLineTool commandLineTool, List<CWLApplicationTemplate> applicationList) {
        DockerRequirement dockerRequirement = CWLUtils.findDockerReq(commandLineTool);
        Container dockerContainer;
        if (dockerRequirement != null) {
            // Based on CWL documentation when dockerPull is provided dockerImageId is ignored (if is present)
            if (dockerRequirement.getDockerPull() != null && dockerRequirement.getDockerPull().isPresent()) {
                /*
                 * In case dockerPull looks like: sen2agri-prod:5000/otb-7-3-0 or orfeotoolbox/otb:9.1.0, extract the container id and return it
                 */
                String dockerImageId = CWLUtils.extractDockerId(dockerRequirement.getDockerPull().get());
                dockerContainer = containerProvider.get(dockerImageId);
                // docker name must be formated because only small caps letters, numbers and dashes are allowed
                String dockerName = CWLUtils.formatDockerName(dockerRequirement.getDockerPull().get());
                if (dockerContainer == null) {
                    Container dockerContainerById = containerProvider.get(dockerImageId);
                    Container dockerContainerByName = null;
                    if (dockerContainerById == null) {
                        //for docker pull the found image must be used
                        dockerContainerById = DockerManager.pullImage(dockerRequirement.getDockerPull().get());
                        dockerContainerByName = containerProvider.getByName(dockerName);
                    }
                    if (dockerContainerById != null && (dockerContainerByName == null
                            || !dockerContainerByName.getId().equals(dockerContainerById.getId()))) {
                        dockerContainer = dockerContainerById;
                        dockerContainer = initializeContainer(dockerContainer, applicationList, dockerName, dockerRequirement.getDockerPull().get());
                        if (dockerContainer != null) {
                            DockerManager.pushImageToRegistry(dockerContainer);
                        }
                    } else {
                        dockerContainer = dockerContainerByName;
                    }
                }
                return dockerContainer;
            } else {
                if (dockerRequirement.getDockerImageId() != null && dockerRequirement.getDockerImageId().isPresent()) {
                    dockerContainer = containerProvider.get(dockerRequirement.getDockerImageId().get());
                    if (dockerContainer != null) {
                        return dockerContainer;
                    }
                    throw new RuntimeException("Container unit is not available, for " + dockerRequirement.getDockerImageId().get()
                            + ". Please use dockerPull with a hosted Docker image instead!");
                }
            }
            if (dockerRequirement.getDockerImport() != null && dockerRequirement.getDockerImport().isPresent()) {
                // TODO: Provide HTTP URL to download and gunzip a Docker images using 'docker import'.
                throw new RuntimeException("The Container unit cannot be created using dockerImport because tar archives are not supported." +
                        " Please use dockerPull with a hosted Docker image instead!");
            }
            if (dockerRequirement.getDockerFile() != null && dockerRequirement.getDockerFile().isPresent()) {
                System.out.println("test");
                // TODO: Supply the contents of a Dockerfile which will be built using docker build.
            }
            if (dockerRequirement.getDockerLoad() != null && dockerRequirement.getDockerLoad().isPresent()) {
                throw new RuntimeException("The Container unit cannot be created using dockerLoad because tar archives are not supported." +
                        " Please use dockerPull with a hosted Docker image instead!");
            }
        }
        return null;
    }

    /**
     * Searches the database for a processing component by its id.
     *
     * @param componentId the processing component id
     * @return the ProcessingComponent if found or null
     */
    private ProcessingComponent findComponent(String componentId) {
        ProcessingComponent processingComponent = componentService.findById(componentId);
        /* Try to see if a simple id is found
         * Example: if component with id otbcli_BandMath is not defined in the database try to find BandMath
         */
        if (processingComponent == null && componentId.contains("_")) {
            componentId = componentId.substring(componentId.lastIndexOf("_") + 1);
            processingComponent = componentService.findById(componentId);
        }
        return processingComponent;
    }

    /**
     * Traverses the CWL workflow structure to find the top-level ancestor id of a given parameter.
     *
     * If the main workflow contains nested workflows, the method follows the references recursively
     * until it finds the root-level parameter connected to the specified child id.
     * If the CWL root is a CommandLineTool, its id is returned directly.
     *
     * @param mainWfId the id of the Workflow used to start the CWL execution
     * @param stepId the step id where the parameter is found
     * @param childId the id of the parameter
     * @param paramType the parameter type (input, output, parameter)
     * @return the ancestor id if found or null
     */
    private String findAncestorId(String mainWfId, String stepId, String childId, String paramType) {
        Object mainWf = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, mainWfId);
        if (mainWf instanceof Workflow) {
            List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream().filter(workflow -> workflow instanceof Workflow).map(obj -> (Workflow) obj).toList();
            String refIn = findAncestorReferencedId(workflowList, stepId, childId, paramType);
            if (workflowList.size() > 1) {
                String ancestorId = null;
                if (refIn != null) {
                    ancestorId = findReferencedIdSourceInRoot(mainWfId, refIn);
                    while (ancestorId == null) {
                        refIn = findAncestorReferencedId(workflowList, stepId, refIn, paramType);
                        if (refIn != null) {
                            ancestorId = findReferencedIdSourceInRoot(mainWfId, refIn);
                        } else {
                            break;
                        }
                    }
               }
                return ancestorId;
            }
            if (refIn != null && refIn.contains("/")) {
                refIn = findAncestorReferencedId(workflowList, stepId, refIn, paramType);
            }
            return refIn;
        } else if (mainWf instanceof CommandLineTool) {
            return mainWfId;
        }
        return null;
    }

    /**
     * Searches existing workflows to find the referenced id for a given parameter id.
     *
     * @param workflowList the list of existing workflows
     * @param stepId the step id where the parameter is found
     * @param id the parameter id
     * @param paramType the parameter type (input, output or parameter)
     * @return the referenced id if found or null
     */
    private String findAncestorReferencedId(List<Workflow> workflowList, String stepId, String id, String paramType) {
        String refRunId = null;
        String paramId;
        String refRunStepId = stepId;
        if (id.contains("/")) {
            id = CWLUtils.getValueFormSource(id);
            String[] splitVal = id.split("/");
            String referredStepId = splitVal[0];
            paramId = splitVal[1];
            Object referredStep = workflowList.stream().flatMap(workflow -> workflow.getSteps().stream())
                    .filter(step -> ((WorkflowStep)step).getId().isPresent() && ((WorkflowStep)step).getId().get().equals(referredStepId)).findFirst().orElse(null);
            if (referredStep != null) {
                for (Object stepOut: ((WorkflowStep) referredStep).getOut()) {
                    if (stepOut instanceof WorkflowStepOutput && ((WorkflowStepOutput) stepOut).getId().isPresent() && ((WorkflowStepOutput) stepOut).getId().get().equals(paramId)) {
                        refRunStepId = ((WorkflowStep) referredStep).getId().get();
                        refRunId = ((WorkflowStep) referredStep).getRun().toString();
                        break;
                    }
                    if (stepOut instanceof String && stepOut.equals(paramId)) {
                        refRunStepId = ((WorkflowStep) referredStep).getId().get();
                        refRunId = ((WorkflowStep) referredStep).getRun().toString();
                        break;
                    }
                }
            }
        } else {
            paramId = id;
            if (paramType.equals(INPUT_PARAM_POSITION)) {
                WorkflowStep referredStep = workflowList.stream().map(workflow -> workflow.getStep(stepId))
                        .filter(Objects::nonNull).findFirst().orElse(null);
                if (referredStep != null) {
                    return referredStep.getIn().stream().filter(in -> ((WorkflowStepInput) in).getId().isPresent() && ((WorkflowStepInput) in).getId().get().equals(paramId))
                            .map(in -> ((WorkflowStepInput) in).getSource()).filter(Objects::nonNull).map(Object::toString).findFirst().orElse(null);
                }
            }
        }
        if (refRunId == null) {
            boolean matchFound = false;
            for (Workflow workflow : workflowList) {
                if (matchFound) break;
                WorkflowStep wfStep = workflow.getStep(refRunStepId);
                if (wfStep != null) {
                    for (Object out : wfStep.getOut()) {
                        if ((out instanceof WorkflowStepOutput && ((WorkflowStepOutput) out).getId().isPresent()
                                && ((WorkflowStepOutput) out).getId().get().equals(paramId))
                                || (out instanceof String && out.equals(paramId))) {
                            refRunStepId = wfStep.getId().get();
                            refRunId = wfStep.getRun().toString();
                            matchFound = true;
                            break;
                        }
                    }
                }
            }
        }
        if (refRunId != null) {
            Object refRunObject = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, refRunId);
            String inReference = null;
            if (refRunObject instanceof CommandLineTool) {
                inReference = ((CommandLineTool) refRunObject).getOutputs().stream()
                        .filter(out -> ((CommandOutputParameter) out).getId().isPresent() && ((CommandOutputParameter) out).getId().get().equals(paramId))
                        .map(out -> ((CommandOutputParameter) out).getOutputBinding()).filter(Objects::nonNull).filter(Optional::isPresent).map(Optional::get)
                        .map(CommandOutputBinding::getGlob).map(Object::toString).findFirst().orElse(null);
            }
            if (inReference != null) {
                inReference = inReference.replaceAll("(\\./)?\\$\\(inputs\\.(.*?)\\)", "$2");
                return findAncestorReferencedId(workflowList, refRunStepId, inReference, INPUT_PARAM_POSITION);
            }
        }
        return null;
    }

    /**
     * Finds the id, used in YAML, of a parameter in the Workflow used to start the CWL execution.
     *
     * @param mainWfId the id of the Workflow used to start the CWL execution
     * @param childId the id of the parameter
     * @return the found id or null
     */
    private String findReferencedIdSourceInRoot(String mainWfId, String childId) {
        return ((Workflow)cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, mainWfId)).getSteps().stream().flatMap(step -> ((WorkflowStep) step).getIn().stream())
                .filter(in -> ((WorkflowStepInput) in).getId().isPresent() && ((WorkflowStepInput) in).getId().get().equals(childId))
                .map(in -> ((WorkflowStepInput) in).getSource()).filter(Objects::nonNull).map(Object::toString).findFirst().orElse(null);
    }

    private Object findParamValue(String mainWfId, String stepId, String parameterKey, String paramType) {
        String inputYmlId = findAncestorId(mainWfId, stepId, parameterKey, paramType);
        Object foundValue;
        if (inputYmlId != null) {
            foundValue = getYamlValue(inputYmlId);
        } else {
            foundValue = findParamDefaultValue(mainWfId, stepId, parameterKey, paramType);
        }
        return foundValue;
    }

    private String findParamDefaultValue(String mainWfId, String stepId, String id, String paramType) {
        Object mainWf = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, mainWfId);
        if (mainWf instanceof Workflow) {
            List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream().filter(workflow -> workflow instanceof Workflow).map(obj -> (Workflow) obj).toList();
            String refRunId = findAncestorReferencedId(workflowList, stepId, id, paramType);
            String paramId;
            String referredStepId;
            if (refRunId != null && refRunId.contains("/")) {
                refRunId = CWLUtils.getValueFormSource(refRunId);
                String[] splitVal = refRunId.split("/");
                referredStepId = splitVal[0];
                paramId = splitVal[1];
                Object referredStep = workflowList.stream().flatMap(workflow -> workflow.getSteps().stream())
                        .filter(step -> ((WorkflowStep)step).getId().isPresent() && ((WorkflowStep)step).getId().get().equals(referredStepId)).findFirst().orElse(null);
                if (referredStep != null) {
                    for (Object stepOut: ((WorkflowStep) referredStep).getOut()) {
                        if (stepOut instanceof WorkflowStepOutput && ((WorkflowStepOutput) stepOut).getId().isPresent() && ((WorkflowStepOutput) stepOut).getId().get().equals(paramId)) {
                            refRunId = ((WorkflowStep) referredStep).getRun().toString();
                            break;
                        }
                        if (stepOut instanceof String && stepOut.equals(paramId)) {
                            refRunId = ((WorkflowStep) referredStep).getRun().toString();
                            break;
                        }
                    }
                }
            } else {
                referredStepId = null;
                paramId = id;
            }
            if (refRunId == null) {
                boolean matchFound = false;
                for (Workflow workflow : workflowList) {
                    if (matchFound) break;
                    for (Object wfStep : workflow.getSteps()) {
                        if (matchFound) break;
                        for (Object out : ((WorkflowStep) wfStep).getOut()) {
                            if (out instanceof WorkflowStepOutput && ((WorkflowStepOutput) out).getId().isPresent() && ((WorkflowStepOutput) out).getId().get().equals(paramId)) {
                                refRunId = ((WorkflowStep) wfStep).getRun().toString();
                                matchFound = true;
                                break;
                            }
                            if (out instanceof String && out.equals(paramId)) {
                                refRunId = ((WorkflowStep) wfStep).getRun().toString();
                                matchFound = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (refRunId != null) {
                Object refRunObject = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, refRunId);
                String inReference = null;
                if (refRunObject instanceof CommandLineTool) {
                    inReference = ((CommandLineTool) refRunObject).getOutputs().stream()
                            .filter(out -> ((CommandOutputParameter) out).getId().isPresent() && ((CommandOutputParameter) out).getId().get().equals(paramId))
                            .map(out -> ((CommandOutputParameter) out).getOutputBinding()).filter(Objects::nonNull).filter(Optional::isPresent).map(Optional::get)
                            .map(CommandOutputBinding::getGlob).map(Object::toString).findFirst().orElse(null);
                }
                if (inReference != null) {
                    inReference = inReference.replaceAll("\\$\\(inputs\\.(.*?)\\)", "$1");
                }
                String inRefParamId = inReference;
                Object referredInput = ((CommandLineTool) refRunObject).getInputs().stream()
                        .filter(in -> ((CommandInputParameter) in).getId().isPresent() && ((CommandInputParameter) in).getId().get().equals(inRefParamId)).findFirst().orElse(null);
                if (referredInput != null) {
                    if (((CommandInputParameter)referredInput).getDefault() != null) {
                        return ((CommandInputParameter)referredInput).getDefault().get().toString();
                    } else {
                        String wfStepId = referredStepId != null ? referredStepId : stepId;
                        String foundValueFrom = findInputValueFromInWfStep(wfStepId, inRefParamId);
                        if (foundValueFrom != null) {
                            return CWLUtils.extractValueFromValueFrom(foundValueFrom);
                        }
                    }
                }
            }
        } else if (mainWf instanceof CommandLineTool) {
            return ((CommandLineTool) mainWf).getInputs().stream()
                    .filter(in -> ((CommandInputParameter) in).getId().isPresent() && ((CommandInputParameter) in).getId().get().equals(id))
                    .map(in -> ((CommandInputParameter) in).getDefault()).filter(Objects::nonNull).filter(Optional::isPresent).map(Optional::get).map(Object::toString)
                    .findFirst().orElse(null);
        }
        return null;
    }

    /**
     * Retrieves the valueFrom value associated with a specific input of a workflow step within the CWL workflow.
     *
     * @param stepId the id of the step where the input is found
     * @param inputId the id of the input
     * @return the valueFrom value as a string or null
     */
    private String findInputValueFromInWfStep(String stepId, String inputId) {
        List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(g -> g instanceof Workflow).map(g -> (Workflow) g).toList();
        if (!workflowList.isEmpty()) {
            for (Workflow workflow : workflowList) {
                WorkflowStep step = workflow.getStep(stepId);
                if (step != null) {
                    return step.getIn().stream()
                            .filter( in -> ((WorkflowStepInput) in).getId().isPresent() && ((WorkflowStepInput) in).getId().get().equals(inputId))
                            .map(in -> ((WorkflowStepInput) in).getValueFrom()).filter(Objects::nonNull).map(Object::toString).findFirst().orElse(null);
                }
            }
        }
        return null;
    }

    /**
     * Updates the workflow, in order to show the input-output connection, that was manually defined in the CWL file.
     * This method links a step input to the output of another step.
     *
     * @param workflow the CWL Workflow to be updated
     * @param yamlInputId the ID of the step input as defined in the YAML
     * @param outputKey the output key to link to
     * @param stepId the id of the step that provides the output
     */
    private void updateWorkflowSteps(Workflow workflow, String yamlInputId, String outputKey, String stepId) {
        boolean found = false;
        for (Object step : workflow.getSteps()) {
            if (found) {break;}
            if (((WorkflowStep)step).getId().get().equals(stepId)) {continue;}
            for (Object in : ((WorkflowStep)step).getIn()) {
                if (((WorkflowStepInput) in).getId() != null && ((WorkflowStepInput) in).getId().isPresent() && ((WorkflowStepInput) in).getId().get().equals(yamlInputId)) {
                    if (((WorkflowStepInput) in).getSource() != null) {
                        Object wfInputType = workflow.getInputs().stream()
                                .filter(wfIn -> ((WorkflowInputParameter) wfIn).getId().isPresent()
                                        && ((WorkflowInputParameter) wfIn).getId().equals(((WorkflowStepInput) in).getId()))
                                .map(wfIn -> ((WorkflowInputParameter) wfIn).getType())
                                .findFirst().orElse(null);
                        if (wfInputType != null && wfInputType.toString().contains("[]")) {
                            List<String> sources = new ArrayList<>();
                            if (((WorkflowStepInput) in).getSource().toString() != null &&
                                    !((WorkflowStepInput) in).getSource().toString().isEmpty()
                                    && !((WorkflowStepInput) in).getSource().toString().equals(((WorkflowStepInput) in).getId().get())) {
                                sources = (List<String>) ((WorkflowStepInput) in).getSource();
                            }
                            sources.add(stepId + "/" + outputKey);
                            ((WorkflowStepInput) in).setSource(sources);
                        } else {
                            ((WorkflowStepInput) in).setSource(stepId + "/" + outputKey);
                        }
                        found = true;
                        break;
                    }
                }
            }
        }
        if (!found) {
            logger.info("Input case not treated for step " + stepId + " in Workflow " + workflow.getId().get());
        }
    }

    /**
     * Searches the input and output values that where manually connected in the CWL file.
     *
     * @param mainWfId the id of the Workflow that is used to start the CWL workflow
     */
    private void updateCwlTemplateWithLinks(String mainWfId) {
        List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(g -> g instanceof Workflow).map(g -> (Workflow) g).toList();
        for (Workflow workflow : workflowList) {
            for (Object step : workflow.getSteps()) {
                for (Object out : ((WorkflowStep)step).getOut()) {
                    Object yamlOutVal;
                    String inputYmlId;
                    String outId = out.toString();
                    if (out instanceof WorkflowStepOutput) {
                        outId = ((WorkflowStepOutput) out).getId().get();
                    }
                    inputYmlId = findAncestorId(mainWfId, ((WorkflowStep) step).getId().get(), outId, OUTPUT_PARAM_POSITION);
                    yamlOutVal = searchOutValInYaml(inputYmlId);
                    if (yamlOutVal instanceof String) {
                        searchLinkInYaml(mainWfId, inputYmlId, outId, yamlOutVal.toString(), ((WorkflowStep) step).getId().get());
                    }
                }
            }
        }
    }

    /**
     * Recursively inspects a CWL workflow to determine whether it contains subWorkflows,
     * and updates workflow step input/output links accordingly.
     *
     * @param mainWfId the id of the Workflow that is used to start the CWL workflow
     * @param inputKey the ID of the step input to be linked
     * @param outputKey the output key to link to
     * @param stepId the id of the step that provides the output
     */
    private void updateWorkflowInputVal(String mainWfId, String inputKey, String outputKey, String stepId) {
        Object mainWorkflow = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, mainWfId);
        if (mainWorkflow instanceof Workflow) {
            boolean hasSubWorkflow = CWLUtils.hasSubWorkflow((Workflow) mainWorkflow);
            if (hasSubWorkflow) {
                updateSubWorkflowSteps((Workflow) mainWorkflow, inputKey, outputKey, stepId);
            } else {
                updateWorkflowSteps((Workflow) mainWorkflow, inputKey, outputKey, stepId);
            }
        } else {
            logger.info("Provided workflow id (" + mainWfId + ") is a CommandLineTool!");
        }
    }

    /**
     * Recursively resolves through nested subWorkflows, until a step is found that does not refer to a subWorkflow.
     * Then, connects the specified output to the specified input.
     *
     * @param workflow the subWorkflow that is used to search for links and next workflow
     * @param yamlInputId the id of the step input to be linked
     * @param outputKey the output id to link to
     * @param stepId the id of the step that provides the output
     */
    private void updateSubWorkflowSteps(Workflow workflow, String yamlInputId, String outputKey, String stepId) {
        boolean found = false;
        String nextWfId = null;
        String nextWfInputId = null;
        for(Object step : workflow.getSteps()) {
            if (found) {break;}
            for(Object in : ((WorkflowStep)step).getIn()) {
                if (((WorkflowStepInput) in).getSource() != null && ((WorkflowStepInput) in).getSource().equals(yamlInputId)) {
                    nextWfId = ((WorkflowStep)step).getRun().toString();
                    if (((WorkflowStepInput) in).getId() != null && ((WorkflowStepInput) in).getId().isPresent() && !((WorkflowStepInput) in).getId().get().isEmpty()) {
                        nextWfInputId = ((WorkflowStepInput) in).getId().get();
                    }
                    found = true;
                    break;
                }
            }
        }

        if (nextWfId != null && nextWfInputId != null) {
            Object nextWorkflow = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, nextWfId);
            if (nextWorkflow instanceof Workflow) {
                boolean hasSubWorkflow = CWLUtils.hasSubWorkflow((Workflow) nextWorkflow);
                if (hasSubWorkflow) {
                    updateSubWorkflowSteps((Workflow) nextWorkflow, nextWfInputId, outputKey, stepId);
                }  else {
                    updateWorkflowSteps((Workflow) nextWorkflow, nextWfInputId, outputKey, stepId);
                }
            } else {
                System.out.println("For dev purposes: can not update workflow step value for input value " + nextWorkflow);
            }
        } else {
            System.out.println("For dev purposes: can not update workflow step value for input value " + yamlInputId + " and workflow id " + workflow.getId().get());
        }

    }

    /**
     * Extracts the container version from a container name.
     * If no version pattern is found, the method returns "latest" as the default version.
     *
     * @param value the full name of the container
     * @return thr extracted version or "latest"
     */
    private String extractContainerVersion(String value) {
        Pattern dotPattern = Pattern.compile("(\\d+[-.]\\d+[-.]\\d+)");
        Matcher matcher = dotPattern.matcher(value);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("-", ".");
        }
        return "latest";
    }

    /**
     * Creates and initializes a new Docker container entity,
     * associates it with the provided CWL applications and saves it to the database.
     *
     * @param container       the container instance to initialize and save
     * @param applicationList the list of CWL application objects associated, used to determine the container's application path
     * @param dockerName      the name of the Docker container, used for naming and tagging the container
     * @return the saved container if successful, or null if an error occurs
     */
    private Container initializeContainer(Container container, List<CWLApplicationTemplate> applicationList, String dockerName, String cwlDeclaredName) {
        Container readContainer;
        try {
            container.setName(dockerName);
            container.setDescription(dockerName + " container");
            container.setTag(extractContainerVersion(cwlDeclaredName));
            container.setType(ContainerType.DOCKER);
            container.setOwnerId(SystemPrincipal.instance().getName());
            container.setVisibility(ContainerVisibility.PRIVATE);
            container.setApplications(new ArrayList<>());
            List<String> applicationPathList = applicationList.stream().map(CWLApplicationTemplate::getApplicationPath).toList();
            container.setApplicationPath(CWLUtils.findContainerPath(applicationPathList, dockerName));
            readContainer = containerProvider.save(container);
        } catch (Exception e) {
            logger.severe(e.getMessage());
            return null;
        }
        return readContainer;
    }

    /**
     * Updates the workflow node, in order to show the input-output connection.
     */
    private void linkWorkflowNodes() {
        for (CWLInputDependenciesTemplate inputDependencies : cwlInputDependencies) {
            WorkflowNodeDescriptor parent = workflowDescriptor.getNodes().stream()
                    .filter(node -> node.getId().equals(inputDependencies.getSourceWorkflowNodeId())
                            && node.getName().equals(inputDependencies.getSourceNodeName()))
                    .findFirst().orElse(null);
            WorkflowNodeDescriptor child = workflowDescriptor.getNodes().stream()
                    .filter(node -> node.getId().equals(inputDependencies.getTargetWorkflowNodeId())
                            && node.getName().equals(inputDependencies.getTargetNodeName()))
                    .findFirst().orElse(null);
            WorkflowNodeDescriptor updatedNode = WorkflowBuild.addLink(parent, child, inputDependencies.getSourceName(), inputDependencies.getTargetName());
            if (updatedNode != null) {
                workflowDescriptor.removeNode(child);
                workflowDescriptor.addNode(updatedNode);
            }
        }
        for (WorkflowNodeDescriptor node : workflowDescriptor.getNodes()) {
            if (node.getIncomingLinks() != null && node.getIncomingLinks().size() > 1) {
                List<Long> sourceIdList = node.getIncomingLinks().stream().map(ComponentLink::getSourceNodeId).toList();
                int maxLevel = workflowDescriptor.getNodeById(sourceIdList.getFirst()).getLevel();
                for (int idx = 1; idx < sourceIdList.size(); idx++) {
                    Long nodeId = sourceIdList.get(idx);
                    int sourceLevel =  workflowDescriptor.getNodeById(nodeId).getLevel();
                    if (sourceLevel > maxLevel) {
                        maxLevel = sourceLevel;
                    }
                }
                if (node.getLevel() <= maxLevel) {
                    node.setLevel(maxLevel + 1);
                }
            }
        }
    }

    /**
     * Checks if the links between workflow nodes are defined in the CWL files.
     * The linking is done in a Workflow, where the input_X from step_X has as value stepY/input_Y
     *
     * @return true or false
     */
    private boolean existCwlLinks() {
        List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(g -> g instanceof Workflow).map(g -> (Workflow) g).toList();
        if (!workflowList.isEmpty()) {
            for (Workflow workflow: workflowList) {
                for (Object step : workflow.getSteps()) {
                    for (Object in : ((WorkflowStep) step).getIn()) {
                        if (((WorkflowStepInput) in).getSource() != null && ((WorkflowStepInput) in).getSource().toString().contains("/")) {
                            String[] parts = ((WorkflowStepInput) in).getSource().toString().split("/");
                            if (parts.length == 2) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Creates a template that shows the input/output connections.
     *
     * @param sourceId the source id
     * @param sourceName the name of the source
     * @param targetId the target id
     * @param targetName the name of the target
     */
    private void addToDependenciesTemplate(String sourceId, String sourceNodeName, String sourceName, String targetId, String targetNodeName, String targetName) {
        CWLInputDependenciesTemplate cwlInputDependenciesTemplate = new CWLInputDependenciesTemplate();
        if (sourceId == null || targetId == null || sourceName == null || targetName == null) {
            throw new RuntimeException("Can not create input dependency list");
        }
        cwlInputDependenciesTemplate.setCommandLineToolTargetId(targetId);
        cwlInputDependenciesTemplate.setSourceName(sourceName);
        cwlInputDependenciesTemplate.setSourceNodeName(sourceNodeName);
        cwlInputDependenciesTemplate.setTargetName(targetName);
        cwlInputDependenciesTemplate.setTargetNodeName(targetNodeName);
        cwlInputDependenciesTemplate.setCommandLineToolSourceId(sourceId);
        cwlInputDependencies.add(cwlInputDependenciesTemplate);
    }

    /**
     * Searches the input value in YAML file.
     *
     * @param inputYmlId the input id, fount in YAML
     * @return the associated value, if found or null
     */
    private Object searchOutValInYaml(String inputYmlId) {
        for (Map.Entry<String, Object> entry : yamlInfo.entrySet()) {
            if (entry.getKey().equals(inputYmlId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Searches the input and output connection that where manually defined in the CWL file.
     *
     * @param mainWfId the id of the Workflow that is used to start the CWL workflow
     * @param inputYmlId the input id found in YAML
     * @param outKey the output id to link to
     * @param yamlOutVal the output value found in YAML
     * @param stepId the id of the step that provides the output
     */
    private void searchLinkInYaml(String mainWfId, String inputYmlId, String outKey, String yamlOutVal, String stepId) {
        for (Map.Entry<String, Object> entry : yamlInfo.entrySet()) {
            if (!entry.getKey().equals(inputYmlId)) {
                if (entry.getValue() instanceof Map<?, ?>) {
                        LinkedHashMap<?, ?> valMap = (LinkedHashMap<?, ?>) entry.getValue();
                        for (Object mapKey : valMap.keySet()) {
                            if (!mapKey.toString().equals("class")) {
                                if (valMap.get(mapKey).toString().contains(yamlOutVal)) {
                                    updateWorkflowInputVal(mainWfId, entry.getKey(), outKey, stepId);
                                }
                            }
                        }
                }
                if (entry.getValue() instanceof Collection<?>) {
                    for (Object arrayVal : (Collection<?>) entry.getValue()) {
                        if(arrayVal instanceof Map<?,?>) {
                            LinkedHashMap<?, ?> valMap = (LinkedHashMap<?, ?>) arrayVal;
                            for (Object mapKey : valMap.keySet()) {
                                if (!mapKey.toString().equals("class")) {
                                    if (valMap.get(mapKey).toString().contains(yamlOutVal)) {
                                        boolean foundLink = isEqualOrFilenameMatch(valMap.get(mapKey).toString(), yamlOutVal);
                                        if (foundLink) {
                                            updateWorkflowInputVal(mainWfId, entry.getKey(), outKey, stepId);
                                        }
                                    }
                                }
                            }
                        } else {
                            logger.info("Case not treated for YAML input instance of class: " + arrayVal.getClass() + " (when searching for Workflow YAML Connection)");
                        }
                    }
                }
                if (entry.getValue() instanceof String) {
                    if (entry.getValue().toString().contains(yamlOutVal)) {
                        boolean foundLink = isEqualOrFilenameMatch(entry.getValue().toString(), yamlOutVal);
                        if (foundLink) {
                            updateWorkflowInputVal(mainWfId, entry.getKey(), outKey, stepId);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if the input value is equal or part of the output value
     *
     * @param inputValue the input value found in YAML
     * @param yamlOutVal the output value found in YAML
     * @return true of false
     */
    private boolean isEqualOrFilenameMatch(String inputValue, String yamlOutVal) {
        if (inputValue.equals(yamlOutVal)) {
            return true;
        }
        return inputValue.contains("/") && inputValue.endsWith(yamlOutVal);
    }

    /**
     * Change processing component category, only for new created components.
     * First take into account the target type.
     * If no target has a specific type (different from OTHER), check the sources.
     */
    private void adjustProcessingComponentsCategory() {
        for (String componentId : createdComponentsHistory) {
            ProcessingComponent processingComponent = componentService.findById(componentId);
            ComponentCategory componentCategory = processingComponent.getCategory();
            if (componentCategory.equals(ComponentCategory.MISC)) {
                List<String> targetsTypeList = processingComponent.getTargets().stream()
                        .filter(target -> !target.getDataDescriptor().getFormatType().equals(DataFormat.OTHER))
                        .map(target -> target.getDataDescriptor().getFormatType().friendlyName().toUpperCase()).toList();
                if (!targetsTypeList.isEmpty()) {
                    String category = targetsTypeList.getFirst();
                    if (targetsTypeList.stream().allMatch(s -> s.equals(category))) {
                        componentCategory = ComponentCategory.valueOf(category);
                    }
                } else {
                    List<String> sourcesTypeList = processingComponent.getSources().stream()
                            .filter(source -> !source.getDataDescriptor().getFormatType().equals(DataFormat.OTHER))
                            .map(source -> source.getDataDescriptor().getFormatType().friendlyName().toUpperCase()).toList();
                    if (!sourcesTypeList.isEmpty()) {
                        String category = sourcesTypeList.getFirst();
                        if (sourcesTypeList.stream().allMatch(s -> s.equals(category))) {
                            componentCategory = ComponentCategory.valueOf(category);
                        }
                    }
                }
                processingComponent.setCategory(componentCategory);
            }
        }
    }

    private void adjustProcessingComponent() {
        List<CommandLineTool> commandLineToolList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(g -> g instanceof CommandLineTool).map(g -> (CommandLineTool) g).toList();
        List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(wf -> wf instanceof Workflow).map(wf -> (Workflow) wf).toList();
        boolean skipCommandLineTool = false;
        boolean wasUpdated = false;
        for (CommandLineTool commandLineTool : commandLineToolList) {
            List<String> wfAssociatedStepIdList = new ArrayList<>();
            if (workflowList != null && !workflowList.isEmpty()) {
                wfAssociatedStepIdList = workflowList.stream().flatMap(wf -> wf.getSteps().stream())
                        .filter(step -> ((WorkflowStep) step).getRun().toString().equals(commandLineTool.getId().get()))
                        .map(step -> (WorkflowStep) step).map(WorkflowStep::getId).map(Optional::get).toList();
                if (wfAssociatedStepIdList != null && !wfAssociatedStepIdList.isEmpty()) {
                    skipCommandLineTool = false;
                } else {
                    skipCommandLineTool = true;
                }
            }
            if (!skipCommandLineTool) {
                List<CWLApplicationTemplate> applicationList = applicationListMap.get(commandLineTool.getId().get());
                for (CWLApplicationTemplate application : applicationList) {
                    boolean updatedComponentSources = false;
                    boolean updatedComponentTargets = false;
                    String componentId = CWLUtils.simplifyApplicationName(application.getApplicationName());
                    ProcessingComponent processingComponent = findComponent(componentId);
                    for (Map.Entry<String, CWLParamDescTemplate> entry : application.getApplicationParams().entrySet()) {
                        String entryKey = CWLUtils.removeNumericSuffix(entry.getKey());
                        String entryFoundName = CWLUtils.removeStartDashes(entry.getValue().getParamName());
                        String parameterKey = entry.getKey();
                        if (entry.getValue().getParamType().equals(OUTPUT_PARAM_POSITION)) {
                            updatedComponentTargets = adjustTagetType(processingComponent, entryKey, entryFoundName, parameterKey, wfAssociatedStepIdList, updatedComponentTargets);
                        }
                        if (entry.getValue().getParamType().equals(INPUT_PARAM_POSITION)) {
                            updatedComponentSources = adjustSourceType(processingComponent, entryKey, entryFoundName, parameterKey, wfAssociatedStepIdList, updatedComponentSources);
                        }
                    }

                    if (updatedComponentSources || updatedComponentTargets) {
                        try {
                            componentService.update(processingComponent);
                            wasUpdated = true;
                        } catch (PersistenceException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        if (wasUpdated) {
            adjustProcessingComponent();
        }
    }

    private boolean adjustTagetType(ProcessingComponent processingComponent, String targetName, String targetFoundName, String parameterKey, List<String> stepIdList, boolean targetModified) {
        TargetDescriptor targetDescriptor = processingComponent.getTargets().stream()
                .filter(pc -> pc.getName().equals(targetName)).findFirst().orElse(null);
        if (targetDescriptor == null) {
            targetDescriptor = processingComponent.getTargets().stream()
                    .filter(pc -> pc.getName().equals(targetFoundName)).findFirst().orElse(null);
        }
        if (targetDescriptor != null && targetDescriptor.getDataDescriptor().getFormatType().equals(DataFormat.OTHER) && !cwlInputDependencies.isEmpty()) {
            List<CWLInputDependenciesTemplate> associatedTargetsList = cwlInputDependencies.stream()
                    .filter(dp -> stepIdList.contains(dp.getSourceNodeName()) && dp.getSourceName().equals(parameterKey)).toList();
            for (CWLInputDependenciesTemplate associatedTarget : associatedTargetsList) {
                List<CWLInputDependenciesTemplate> sourcesList = cwlInputDependencies.stream()
                        .filter(dp -> dp.getTargetNodeName().equals(associatedTarget.getTargetNodeName())
                                && dp.getTargetApplicationName() != null).toList();
                for (CWLInputDependenciesTemplate template : sourcesList) {
                    ProcessingComponent associatedComponent = findComponent(template.getTargetApplicationName());
                    Object sourceCommandLineTool = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, template.getCommandLineToolTargetId());
                    if (sourceCommandLineTool instanceof CommandLineTool) {
                        Map<String, CWLParamDescTemplate> params = applicationListMap.get(((CommandLineTool) sourceCommandLineTool).getId().get()).getFirst().getApplicationParams();
                        String sourceName = params.get(template.getTargetName()).getParamName();
                        SourceDescriptor templateSource = associatedComponent.getSources().stream()
                                .filter(pc -> pc.getName().equals(sourceName)).findFirst().orElse(null);
                        if (templateSource != null && !templateSource.getDataDescriptor().getFormatType().equals(DataFormat.OTHER)) {
                            targetDescriptor.setDataDescriptor(templateSource.getDataDescriptor());
                            targetModified = true;
                            break;
                        }
                    }
                }
            }
        }
        return targetModified;
    }

    private boolean adjustSourceType(ProcessingComponent processingComponent, String sourceName, String sourceFoundName, String parameterKey, List<String> stepIdList, boolean sourceModified) {
        SourceDescriptor sourceDescriptor = processingComponent.getSources().stream()
                .filter(pc -> pc.getName().equals(sourceName)).findFirst().orElse(null);
        if (sourceDescriptor == null) {
            sourceDescriptor = processingComponent.getSources().stream()
                    .filter(pc -> pc.getName().equals(sourceFoundName)).findFirst().orElse(null);
        }
        if (sourceDescriptor != null && sourceDescriptor.getDataDescriptor().getFormatType().equals(DataFormat.OTHER) && !cwlInputDependencies.isEmpty()) {
            List<CWLInputDependenciesTemplate> associatedSourcesList = cwlInputDependencies.stream()
                    .filter(dp -> stepIdList.contains(dp.getTargetNodeName()) && dp.getTargetName().equals(parameterKey)).toList();
            for (CWLInputDependenciesTemplate associatedSources : associatedSourcesList) {
                ProcessingComponent associatedComponent = findComponent(associatedSources.getSourceApplicationName());
                Object targetCommandLineTool = cwlTemplate.getCwlGraphChild(CWL_GRAPH_ID, associatedSources.getCommandLineToolSourceId());
                if (targetCommandLineTool instanceof CommandLineTool) {
                    Map<String, CWLParamDescTemplate> params = applicationListMap.get(((CommandLineTool) targetCommandLineTool).getId().get()).getFirst().getApplicationParams();
                    String targetName = params.get(associatedSources.getSourceName()).getParamName();
                    TargetDescriptor templateTarget = associatedComponent.getTargets().stream()
                            .filter(pc -> pc.getName().equals(targetName)).findFirst().orElse(null);
                    if (templateTarget != null && !templateTarget.getDataDescriptor().getFormatType().equals(DataFormat.OTHER)) {
                        sourceDescriptor.setDataDescriptor(templateTarget.getDataDescriptor());
                        sourceModified = true;
                        break;
                    }
                }
            }
        }
        return sourceModified;
    }

    /**
     * Checks if the input of a component is linked with te output of another.
     *
     * @param inputId the id of an input
     * @return true or false
     */
    private boolean checkIfInputIsLinked(String inputId) {
        List<Workflow> workflowList = cwlTemplate.getCwlMainGraphObject(CWL_GRAPH_ID).stream()
                .filter(g -> g instanceof Workflow).map(g -> (Workflow) g).toList();
        if (!workflowList.isEmpty()) {
            for (Workflow workflow: workflowList) {
                for (Object step : workflow.getSteps()) {
                    WorkflowStepInput wfInput = ((WorkflowStep) step).getIn().stream()
                            .filter(in -> ((WorkflowStepInput) in).getId().isPresent() && ((WorkflowStepInput) in).getId().get().equals(inputId))
                            .map(in -> (WorkflowStepInput) in).findFirst().orElse(null);
                    if (wfInput != null && wfInput.getSource() != null && wfInput.getSource().toString().contains("/")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
