package ro.cs.tao.utils.constants;

public class ArgoConstants {
    public final static String ARGO_KIND = "Workflow";
    public final static String ARGO_VERSION = "argoproj.io/v1alpha1";
    public final static String ENTRYPOINT = "workflow-start";
    public final static String MAIN_WF_INPUT_PREFIX = "wf-input-";
    public final static String MAIN_WF_OUTPUT_PREFIX = "wf-out-";
    public final static String CONNECTOR_STRING = "-";

    public static String replaceWithConnectorString(String s){
        return s.replaceAll("_", CONNECTOR_STRING);
    }
}
