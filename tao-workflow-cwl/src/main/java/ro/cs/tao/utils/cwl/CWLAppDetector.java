package ro.cs.tao.utils.cwl;

import org.w3id.cwl.cwl1_2.*;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ro.cs.tao.utils.cwl.CWLConstants.*;
import static ro.cs.tao.utils.cwl.CWLConstants.OUTPUT_PARAM_POSITION;

public class CWLAppDetector {
    private static final Logger logger = Logger.getLogger(CWLAppDetector.class.getName());

    /**
     * Finds the application run by a CWL tool from baseCommand.
     *
     * @param baseCommand the list of command tokens
     * @return the found application string
     */
    public static String findApplication(List<String> baseCommand) {
        if (baseCommand == null || baseCommand.isEmpty()) {
            return null;
        }
        String first = baseCommand.get(0);
        // Check if first token matches a python interpreter pattern (e.g., python, python3.8)
        boolean isPython = first.matches("python(\\d+(\\.\\d+)?)?");

        if (isInterpreter(first) || isPython) {
            if (baseCommand.size() > 1) {
                return baseCommand.get(1);
            } else {
                return first;
            }
        } else {
            // If it's not an interpreter, return the full command or just the first part
            return String.join(" ", baseCommand);
        }
    }

    /**
     * Creates a list of application template that contains the application name, path and params.
     *
     * @param commandLineTool the CWL object with class commandLineTool
     * @param inputs the list of inputs found in Workflow associated step for the commandLineTool
     * @return the list of application template
     */
    public static List<CWLApplicationTemplate> createApplicationTemplate(CommandLineTool commandLineTool, List<Object> inputs) {
        List<CWLApplicationTemplate> componentList = new LinkedList<>();
        List<String> baseCommand = (List<String>) commandLineTool.getBaseCommand();
        boolean isBashWrapper = baseCommand != null &&
                baseCommand.equals(Arrays.asList("/bin/bash", "-c"));
        if (isBashWrapper) {
            if (commandLineTool.getArguments().isPresent()) {
                for (Object arg : commandLineTool.getArguments().get()) {
                    if (arg instanceof CommandLineBinding) {
                        if (((CommandLineBinding) arg).getValueFrom() != null) {
                            Map<String ,String> applicationList = extractApplications(String.valueOf(((CommandLineBindingImpl) arg).getValueFrom()));
                            for (Map.Entry<String, String> app : applicationList.entrySet()) {
                                CWLApplicationTemplate applicationTemplate = new CWLApplicationTemplate();
                                extractApplicationInfoToTemplate(applicationTemplate, app.getKey());
                                applicationTemplate.setApplicationParams(findApplicationParams(commandLineTool, app.getValue()));
                                componentList.add(applicationTemplate);
                            }
                        }
                    } else {
                        throw new RuntimeException("Unsupported argument type: " + arg.getClass().getName()
                                + " for CommandLineTool "
                                + (commandLineTool.getId().isPresent() ? commandLineTool.getId().get() : ""));
                    }
                }
            }
            logger.info("For dev purposes: valueFrom not found in arguments. In may be found in inputs, but is not treated");
        } else {
            String application = findApplication(baseCommand);
            if (application != null) {
                CWLApplicationTemplate applicationTemplate = new CWLApplicationTemplate();
                extractApplicationInfoToTemplate(applicationTemplate, application);
                applicationTemplate.setApplicationParams(findApplicationParams(commandLineTool, null));
                componentList.add(applicationTemplate);
            } else {
                logger.severe("No application found in baseCommand. Please check that the baseCommand is correctly defined and follows the expected format.");
            }
        }
        if (inputs != null && !inputs.isEmpty()) {
            List<String> linkedInputsIds =  new ArrayList<>();
            for (Object in : inputs) {
                if (((WorkflowStepInput) in).getSource() != null && ((WorkflowStepInput) in).getId() != null
                        && ((WorkflowStepInput) in).getId().isPresent()) {
                    if (((WorkflowStepInput) in).getSource().toString().matches("^(?:[\\w-]+/[\\w-]+|\\[([\\w-]+/[\\w-]+(?:,\\s?[\\w-]+/[\\w-]+)*)\\])$")) {
                        linkedInputsIds.add(((WorkflowStepInput) in).getId().get());
                    }
                }
            }
            for (CWLApplicationTemplate applicationTemplate : componentList) {
                for (Map.Entry<String, CWLParamDescTemplate> entry : applicationTemplate.getApplicationParams().entrySet()) {
                    if (entry.getValue().getParamType().equals(PARAMETER_PARAM_POSITION)) {
                        if (entry.getValue().getParamName().matches("processor(.\\w+)*") && entry.getValue().getFormatType().matches("path(\\[\\])?")) { //this works for ProductFormatter
                            entry.getValue().setParamType(INPUT_PARAM_POSITION);
                        } else {
                            boolean isSource = linkedInputsIds.stream().anyMatch(id -> id.equals(entry.getKey()));
                            if (isSource) {
                                entry.getValue().setParamType(INPUT_PARAM_POSITION);
                            }
                        }
                    }
                    if (entry.getValue().getDefaultValue() == null) {
                        WorkflowStepInput associatedStepIn = inputs.stream().filter(in -> ((WorkflowStepInput) in).getId().isPresent()
                                        && (((WorkflowStepInput) in).getId().get().equals(entry.getKey())) || ((WorkflowStepInput) in).getId().get().equals(entry.getValue().getParamName()))
                                .map(in -> (WorkflowStepInput) in).filter(in -> in.getValueFrom() != null).findFirst().orElse(null);
                        if (associatedStepIn != null) {
                            String value = CWLUtils.extractValueFromValueFrom(associatedStepIn.getValueFrom().toString());
                            entry.getValue().setDefaultValue(value);
                        }
                    }
                }
            }
        }
        return componentList;
    }

    /**
     * Populates application template with information: application mane and path.
     *
     * @param applicationTemplate the application template
     * @param applicationInfo found application
     */
    private static void extractApplicationInfoToTemplate(CWLApplicationTemplate applicationTemplate, String applicationInfo) {
        String applicationName = applicationInfo;
        String applicationPath = applicationInfo;
        if (applicationInfo.contains(" ")) {
            String[] tokens = applicationInfo.split(" ");
            if (tokens.length > 2) {
                logger.warning("Maybe wrong application definition! Please check the baseCommand setting or valueFrom expressions from your CWL files!");
            }
            applicationPath = tokens[0];
            applicationName = tokens[1];
        } else if (applicationInfo.contains("/")) {
            applicationName = applicationInfo.substring(applicationInfo.lastIndexOf("/") + 1);
            applicationPath = applicationInfo.substring(0, applicationInfo.lastIndexOf("/"));
        } else {
            logger.severe("Please check that the baseCommand is correctly defined and follows the expected format.");
        }
        applicationTemplate.setApplicationName(applicationName);
        applicationTemplate.setApplicationPath(applicationPath);
    }

    /**
     * Creates a mapping of CWL parameter descriptions for a CommandLineTool.
     *
     * @param commandLineTool the CWL object with class commandLineTool
     * @param argCommands the application command line from CWL file, if defined, otherwise null
     *
     * @return a map where key is the input/output id and value is a template that describes the parameter metadata
     */
    private static Map<String, CWLParamDescTemplate> findApplicationParams(CommandLineTool commandLineTool, String argCommands) {
        Map<String, CWLParamDescTemplate> applicationTemplates = new LinkedHashMap<>();
        if (argCommands != null) {
            String[] commands = argCommands.split("\\s");
            for (int idx = 1; idx < commands.length; idx++) {
                if (commands[idx].startsWith("-")) {
                    addParamInfoToMap(applicationTemplates, commandLineTool, commands[idx], commands[idx + 1]);
                    idx++;
                }
            }
        } else {
            addParamInfoToMap(applicationTemplates, commandLineTool);
        }
        return applicationTemplates;
    }

    /**
     * Populates the parameter description map with metadata for all input and output
     * parameters defined in the current CommandLineTool.
     *
     * @param applicationTemplates a map where key is the input/output id and value is a template that describes the parameter metadata
     * @param commandLineTool the CWL object with class commandLineTool
     */
    private static void addParamInfoToMap(Map<String, CWLParamDescTemplate> applicationTemplates, CommandLineTool commandLineTool) {
        for (Object input : commandLineTool.getInputs()) {
            boolean canChangeMandatoryParamType = false; //added as workaround for Sen4XRadiometricIndices -list param (it must be mandatory but still parameter)
            CWLParamDescTemplate paramDesc = new CWLParamDescTemplate();
            String paramFormatType = extractParamType(((CommandInputParameter) input).getType());
            paramDesc.setParamType(INPUT_PARAM_POSITION);
            if ((!(((CommandInputParameter)input).getType() instanceof Collection)
                    && !((CommandInputParameter) input).getType().toString().matches("^(File|Directory)(\\[])?\\??$"))
                    || ((CommandInputParameter) input).getId().get().matches(".*_?config_?.*")) {
                paramDesc.setParamType(PARAMETER_PARAM_POSITION);
            }
            paramDesc.setFormatType(paramFormatType);
            // an input is optional if type is a CWLType, CWLType array or a string that ends with question mark
            if ((((CommandInputParameter) input).getType() instanceof CWLType && ((CommandInputParameter) input).getType().toString().endsWith("?")) ||
                    ((CommandInputParameter) input).getType() instanceof Collection<?> ||
                    (((CommandInputParameter) input).getDefault() != null
                            && ((CommandInputParameter) input).getDefault().get() instanceof Collection<?>
                            && ((Collection<?>) ((CommandInputParameter) input).getDefault().get()).isEmpty())) {
                // condition for type:string?. The CWL loading library returns it as CWLType array with [null, string]
                if (((CommandInputParameter) input).getType() instanceof Collection<?>) {
                    boolean findNull = ((Collection<?>) ((CommandInputParameter) input).getType()).stream()
                            .anyMatch(elem -> elem instanceof CWLType && elem.toString().equals("null"));
                    if (findNull) {
                        paramDesc.setMandatory(false);
                        //paramDesc.setFormatType(paramFormatType);
                    } else {
                        paramDesc.setMandatory(true);
                        canChangeMandatoryParamType = true;
                    }
                } else {
                    paramDesc.setMandatory(false);
                }
            } else {
                paramDesc.setMandatory(true);
            }

            if (canChangeMandatoryParamType || !paramDesc.isMandatory()) {
                paramDesc.setParamType(PARAMETER_PARAM_POSITION);
            }

            if (((CommandInputParameter) input).getInputBinding() != null
                    && ((CommandInputParameter) input).getInputBinding().isPresent()) {
                CommandLineBinding inputBinding = ((CommandInputParameter) input).getInputBinding().get();
                paramDesc.setParamName(((CommandInputParameter) input).getId().get());
                if (inputBinding.getPrefix() != null && inputBinding.getPrefix().isPresent()) {
                    String prefix = inputBinding.getPrefix().get();
                    paramDesc.setParamPrefix(prefix);
                } else if (inputBinding.getPosition() != null) {
                    if (inputBinding.getPosition() instanceof Integer) {
                        paramDesc.setParamPosition((Integer) inputBinding.getPosition());
                    } else {
                        logger.info("Param position type is not integer as expected. Se the parameter " + ((CommandInputParameter) input).getId().get() + " in your CWL files");
                    }
                } else {
                    logger.warning("No position or prefix found for " + (((CommandInputParameter) input).getId().isPresent() ? ((CommandInputParameter) input).getId().get() : "inputs")
                            + " in CommandLineTool " + (commandLineTool.getId().isPresent() ? commandLineTool.getId().get() : " without id"));
                    paramDesc.setParamName(((CommandInputParameter) input).getId().get());
                }
            } else {
                logger.warning("No input binding found for " + (((CommandInputParameter) input).getId().isPresent() ? ((CommandInputParameter) input).getId().get() : "inputs")
                        + " in CommandLineTool " + (commandLineTool.getId().isPresent() ? commandLineTool.getId().get() : " without id"));
                paramDesc.setParamName(((CommandInputParameter) input).getId().get());
                continue;
            }
            if (((CommandInputParameterImpl) input).getDefault() != null && ((CommandInputParameterImpl) input).getDefault().isPresent()) {
                paramDesc.setDefaultValue(((CommandInputParameterImpl) input).getDefault().get());
            }
            if (((CommandInputParameter) input).getId().isPresent()) {
                applicationTemplates.put(((CommandInputParameter) input).getId().get(), paramDesc);
            } else {
                logger.warning("Skipped input in CommandLineTool " + (commandLineTool.getId().isPresent() ? commandLineTool.getId().get() : " (without id)"));
            }
        }
        for (Object output : commandLineTool.getOutputs()) {
            CWLParamDescTemplate paramDesc = new CWLParamDescTemplate();
            String paramFormatType = extractParamType(((CommandOutputParameter) output).getType());
            paramDesc.setParamType(INPUT_PARAM_POSITION);
            paramDesc.setFormatType(paramFormatType);
            if (((CommandOutputParameter) output).getOutputBinding().isPresent() && ((CommandOutputParameter) output).getOutputBinding().get().getGlob() != null) {
                String glob = ((CommandOutputParameter) output).getOutputBinding().get().getGlob().toString();
                String paramIdRegex = "^\"?(\\./)?\\$\\(inputs\\.([a-zA-Z0-9_]+)(\\)?/\\*)?\\)?(\\s?\\?.+)?\"?$";
                Pattern patternIdRegex = Pattern.compile(paramIdRegex);
                Matcher matcherIdRegex = patternIdRegex.matcher(glob);
                if (matcherIdRegex.find()) {
                    String paramId = matcherIdRegex.group(2);
                    if(applicationTemplates.get(paramId) != null) {
                        paramDesc = applicationTemplates.get(paramId);
                        paramDesc.setParamType(OUTPUT_PARAM_POSITION);
                        // remove it from inputs
                        applicationTemplates.remove(paramId);
                    }
                } else {
                    logger.warning("Output with outputBinding glob: " + glob + " is not treated!");
                    continue;
                }
            } else {
                logger.warning("No binding found for " + (((CommandOutputParameter) output).getId().isPresent() ? ((CommandOutputParameter) output).getId().get() : "outputs")
                        + " in CommandLineTool " + (commandLineTool.getId().isPresent() ? commandLineTool.getId().get() : " without id"));
                continue;
            }
            if (((CommandOutputParameter) output).getId().isPresent()) {
                applicationTemplates.put(((CommandOutputParameter) output).getId().get(), paramDesc);
            } else {
                logger.warning("Skipped input in CommandLineTool " + (commandLineTool.getId().isPresent() ? commandLineTool.getId().get() : " (without id)"));
            }
        }
    }

    /**
     * Adds the metadata information to map.
     *
     * @param applicationTemplates a map where key is the input/output id and value is a template that describes the parameter metadata
     * @param commandLineTool the CWL object with class commandLineTool
     * @param paramName the parameter name
     * @param paramValue the parameter value
     */
    private static void addParamInfoToMap(Map<String, CWLParamDescTemplate> applicationTemplates, CommandLineTool commandLineTool, String paramName, String paramValue) {
        CWLParamDescTemplate paramDesc = new CWLParamDescTemplate();
        Object definedType = null;
        String paramPosition = null;
        String paramId = "";
        String paramIdRegex = "\\$\\((inputs|outputs)\\.([a-zA-Z0-9_]+)(([\\[\\]]+)?([\\w./]+)?\\))?\\s?";
        Pattern patternIdRegex = Pattern.compile(paramIdRegex);
        Matcher matcherIdRegex = patternIdRegex.matcher(paramValue);
        if (matcherIdRegex.find()) {
            paramId = matcherIdRegex.group(2);
        }
        String tempParamId = paramId;
        if (paramName.startsWith("-")) {
            paramName = CWLUtils.removeStartDashes(paramName);
        }

        if (paramValue.matches("(\"?\\$\\(inputs\\."+paramId+"[\\w\\[\\].]*\\)\"?([\\w./]+)?\\s?)*")) {
            Optional<CommandOutputParameter> outParam = commandLineTool.getOutputs().stream()
                    .map(out -> (CommandOutputParameter)out)
                    .filter(out -> {
                        if (out.getOutputBinding() != null && out.getOutputBinding().isPresent()) {
                            return out.getOutputBinding().get().getGlob().toString().equalsIgnoreCase(paramValue);
                        } else {
                            return out.getId().isEmpty() ? null : out.getId().get().equals(tempParamId);
                        }
                    }).findFirst();
            if (outParam.isPresent()) {
                paramPosition = OUTPUT_PARAM_POSITION;
                definedType = outParam.get().getType();
                paramId = outParam.get().getId().isPresent() ? outParam.get().getId().get() : "";
            } else {
                paramPosition = INPUT_PARAM_POSITION;
                Optional<CommandInputParameter> inputParam = commandLineTool.getInputs().stream()
                        .map(in -> (CommandInputParameter) in)
                        .filter(in -> in.getId().isPresent() && in.getId().get().equals(tempParamId))
                        .findFirst();
                definedType = inputParam.isPresent() ? inputParam.get().getType() : "";
                if (!definedType.toString().matches("^(File|Directory)(\\[])?\\??$")) {
                    paramPosition = PARAMETER_PARAM_POSITION;
                }
            }
        } else if (paramValue.matches("(\"?\\$\\(outputs\\."+paramId+"[\\w\\[\\].]*\\)\"?([\\w./]+)?\\s?)*")){
            definedType = commandLineTool.getOutputs().stream()
                    .map(out -> (CommandOutputParameter) out)
                    .filter(out -> out.getId().isPresent() && out.getId().get().equals(tempParamId))
                    .findFirst()
                    .map(CommandOutputParameter::getType)
                    .orElse(null);
            paramPosition = OUTPUT_PARAM_POSITION;
        }
        paramDesc.setParamType(paramPosition);
        if (definedType != null && definedType.toString().endsWith("?")) {
            paramDesc.setMandatory(false);
        } else {
            paramDesc.setMandatory(true);
        }
        String paramType = extractParamType(definedType);
        paramDesc.setFormatType(paramType);
        paramDesc.setParamName(paramName);
        applicationTemplates.put(paramId, paramDesc);
    }

    /**
     * Checks if the string is a string represent a known interpreter.
     *
     * @param cmd the command to be checked
     * @return true or false
     */
    private static boolean isInterpreter(String cmd) {
        return INTERPRETERS.contains(cmd) || INTERPRETERS.contains(getBasename(cmd));
    }

    /**
     * Extracts the final path component from a given path.
     *
     * @param path the string from which to extract the basename
     * @return the basename of the given path
     */
    private static String getBasename(String path) {
        int lastSlash = path.lastIndexOf('/');
        return (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Extracts the application from a string.
     *
     * @param command the string that represents a command line
     * @return a map with key the application name and the value is the full command from where the application is extracted
     */
    private static Map<String ,String> extractApplications(String command) {
        Map<String, String> applications = new LinkedHashMap<>();
        // Normalize line breaks and backslashes
        String normalized = command.replaceAll("\\\\\n", " ").replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ").trim();
        // Split on shell command delimiters
        String[] commandParts = normalized.split("&&|;|\\|");
        for (String part : commandParts) {
            // Tokenize command by whitespace
            String[] tokens = part.trim().split("\\s+");
            for (String token : tokens) {
                // Skip empty tokens or variable expressions or cp, rm command
                if (token.isEmpty() || token.startsWith("-") || token.startsWith("$(")
                        || token.startsWith("\"") || token.equals("\\")
                        || token.equals("cp") || token.equals("rm")) {
                    continue;
                }
                // First usable token is the application
                applications.put(token, part);
                break;
            }
        }
        return applications;
    }

    /**
     * Extracts and normalizes a parameter type name from a given type object.
     *
     * @param typeObject an object representing a CWL parameter type
     * @return the normalized lowercase type name or null
     */
    private static String extractParamType(Object typeObject) {
        if (typeObject != null) {
            String type = typeObject.toString();
            if (typeObject instanceof Collection<?>) {
                Object nonNull = ((Collection<?>) typeObject).stream()
                        .filter(elem -> (elem instanceof CWLType && !elem.toString().equals("null")) || elem instanceof CWLArraySchema)
                        .findFirst().orElse(null);
                if (nonNull != null) {
                    type = nonNull.toString();
                } else {
                    type = ((Collection<?>)typeObject).stream().findFirst().orElse(null).toString();
                }
            }
            if (type.endsWith("?")) {
                type = type.substring(0, type.length() - 1);
            }
            if (type.matches("^(File|Directory)(\\[])?\\??$")) {
                return type.replaceAll("(File|Directory)", "Path").toLowerCase();
            }
            return type.toLowerCase();
        }
        return null;
    }
}
