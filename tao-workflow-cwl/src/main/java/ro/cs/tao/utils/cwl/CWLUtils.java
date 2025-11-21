package ro.cs.tao.utils.cwl;

import org.w3id.cwl.cwl1_2.*;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CWLUtils {

    private static final Logger logger = Logger.getLogger(CWLUtils.class.getName());

    public static String generateId(String value) {
        return value.substring(0, value.lastIndexOf(".")).replace(".", "_").toLowerCase();
    }

    public static List<String> getFileIdsList(String input) {
        List<String> foundIdList = new ArrayList<>();
        Pattern idPattern = Pattern.compile("(?<![a-zA-Z_])id=([^,}]+)");

        Matcher idMatcher = idPattern.matcher(input);
        while (idMatcher.find()) {
            foundIdList.add(idMatcher.group(1).trim());
        }
        return foundIdList;
    }

    public static String findContainerPath(List<String> applicationPathList, String dockerName) {
        String applicationPath = null;
        for (String path : applicationPathList) {
            if (applicationPath != null) {
                if (!path.equalsIgnoreCase(applicationPath)) {
                    applicationPath = commonDirectory(applicationPath, path);
                } else {
                    logger.warning("Found more than one application path for " + dockerName + " container");
                }
            } else {
                applicationPath = path;
            }
        }
        return applicationPath;
    }

    public static ArrayList<String> findSourceValues(Object sourceValue) {
        ArrayList<String> foundSourceList = new ArrayList<>();
        if (sourceValue instanceof Collection<?>) {
            foundSourceList.addAll(((ArrayList<?>) sourceValue).stream()
                    .map(Object::toString).collect(Collectors.toList()));
        } else {
            foundSourceList.add(sourceValue.toString());
        }
        return foundSourceList;
    }

    public static String simplifyApplicationName(String name, boolean toLowerCase) {
        if (name.contains("otbcli_")) {
            name = name.replace("otbcli_", "");
        }
        if (name.contains(".")) {
            int lastDot = name.lastIndexOf('.');
            name = name.substring(0, lastDot);
        }
        //name = name.replaceAll("[_|\\-|\\.]", " ");
        String[] tokens = name.split("_|\\-|\\.");
        name = Arrays.stream(tokens).map(token -> token.isEmpty() ? token : token.substring(0, 1).toUpperCase() + token.substring(1)).collect(Collectors.joining(""));
        if (toLowerCase) {
            return name.toLowerCase();
        }
        return name;
    }

    public static String commonDirectory(String s1, String s2) {
        int len = Math.min(s1.length(), s2.length());
        int i = 0;
        while (i < len && s1.charAt(i) == s2.charAt(i)) i++;
        int lastSlash = s1.lastIndexOf('/', i - 1);
        return lastSlash >= 0 ? s1.substring(0, lastSlash + 1) : "";
    }

    public static String formatDockerName (String name) {
        if (name.matches("^\\w+:\\d+\\/.+")) {
            int fistSlashIdx = name.indexOf("/");
            name = name.substring(fistSlashIdx + 1);
        }
        if (name.matches(".+:\\d+\\.\\d+\\.\\d+")) {
            int lastColonIdx = name.lastIndexOf(":");
            name = name.substring(0, lastColonIdx);
        }
        name = name.replaceAll("[_|:|\\.|/|\\s]", "-");
        return name.toLowerCase();
    }

    public static String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot != -1 && lastDot < filePath.length() - 1) {
            int lastSlash = filePath.lastIndexOf("/");
            if (lastSlash == filePath.length() - 1) {
                return filePath.substring(lastDot, filePath.length() - 1).toLowerCase();
            }
            return filePath.substring(lastDot).toLowerCase();
        }
        return "";
    }

    public static DockerRequirement findDockerReq(CommandLineTool commandLineTool) {
        Optional<DockerRequirement> dockerRequirement = Optional.empty();
        if (commandLineTool.getRequirements() != null && commandLineTool.getRequirements().isPresent()) {
            dockerRequirement = commandLineTool.getRequirements().get().stream()
                    .filter(c -> c instanceof DockerRequirement)
                    .map(c -> (DockerRequirement) c).findFirst();
        }
        if (dockerRequirement.isEmpty() && commandLineTool.getHints() != null && commandLineTool.getHints().isPresent()) {
            dockerRequirement = commandLineTool.getHints().get().stream()
                    .filter(c -> c instanceof DockerRequirement)
                    .map(c -> (DockerRequirement) c).findFirst();
        }
        return dockerRequirement.orElse(null);
    }

    public static String extractDockerId(String value) {
        String regex = "^[\\w.-]+:?\\d*/[\\w.:-]+$";
        if (value.matches(regex)) {
            int charIndex = value.indexOf("/");
            return value.substring(charIndex + 1);
        }
        return value;
    }

    public static String findAssociatedStepId(String id, List<Workflow> workflowList) {
        for (Workflow workflow: workflowList) {
            for (Object step : workflow.getSteps()) {
                if (((WorkflowStep) step).getId().isPresent() && ((WorkflowStep) step).getId().get().equals(id)) {
                    return ((WorkflowStep) step).getId().get();
                }
            }
        }
        return null;
    }

    public static String findAssociatedRun(String id, List<Workflow> workflowList) {
        for (Workflow workflow: workflowList) {
            for (Object step : workflow.getSteps()) {
                if (((WorkflowStep) step).getId().isPresent() && ((WorkflowStep) step).getId().get().equals(id)) {
                    return ((WorkflowStep) step).getRun().toString();
                }
            }
        }
        return null;
    }

    public static String extractDashPrefix(String value) {
        Matcher m = Pattern.compile("^-+").matcher(value);
        String prefix = m.find() ? m.group() : "";
        return prefix;
    }

    public static String removeStartDashes(String value) {
        if(value.startsWith("-")) {
            String tempVal = value;
            while (tempVal.startsWith("-")) {
                tempVal = tempVal.substring(1);
            }
           return tempVal;
        }
        return value;
    }

    public static String removeNumericSuffix(String name) {
        Pattern namePattern = Pattern.compile("_\\d+$");
        Matcher nameMatcher = namePattern.matcher(name);
        if (nameMatcher.find()) {
            return name.replace(nameMatcher.group(0), "");
        }
        return name;
    }

    public static boolean hasSubWorkflow(Workflow workflow) {
        if (workflow.getRequirements() != null && workflow.getRequirements().isPresent() && !workflow.getRequirements().get().isEmpty()) {
            return  workflow.getRequirements().get().stream().anyMatch(req -> req instanceof SubworkflowFeatureRequirement);
        }
        if (workflow.getHints() != null && workflow.getHints().isPresent() && !workflow.getHints().get().isEmpty()) {
            return  workflow.getHints().get().stream().anyMatch(hint -> hint instanceof SubworkflowFeatureRequirement);
        }
        return false;
    }

    /**
     * Transforms the component name to component id, keeping the component name sufix if exist.
     *
     * @param name the component name
     * @param componentId the component id
     * @return
     */
    public static String transformFromNameToCompId(String name, String componentId) {
        String nameSuffix = "";
        Pattern namePattern = Pattern.compile(".*(_\\d+)$");
        Matcher nameMatcher = namePattern.matcher(name);
        if (nameMatcher.find()) {
            nameSuffix = nameMatcher.group(1);
            // transform sufix from _\d+ to \s(_\d+)
            nameSuffix = nameSuffix.replace("_", " (") + ")";
            // add the new sufix to name in order to transform name from gdal_translate_1 to gdal_translate (1)
            name = name + nameSuffix;
        }
        return componentId + nameSuffix;
    }

    public static String getValueFormSource(String sourceValue) {
        if (sourceValue.matches("^\\[((\\w|-|\\d)+\\/.+)+\\]$")) {
            if (sourceValue.contains(",")) {
                sourceValue = sourceValue.split(",")[0].replaceAll("]|\\[|\\s", "");
            } else {
                sourceValue = sourceValue.replaceAll("]|\\[|\\s|", "");
            }
        }
        return sourceValue;
    }
}
