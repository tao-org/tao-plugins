package ro.cs.tao.utils.cwl;

public class CWLInputDependenciesTemplate {
    private String commandLineToolSourceId;
    private String commandLineToolTargetId;
    private String sourceNodeName;
    private String targetNodeName;
    private String sourceName;
    private String targetName;
    private Long SourceWorkflowNodeId;
    private Long targetWorkflowNodeId;

    public String getCommandLineToolSourceId() {
        return commandLineToolSourceId;
    }

    public void setCommandLineToolSourceId(String commandLineToolSourceId) {
        this.commandLineToolSourceId = commandLineToolSourceId;
    }

    public String getCommandLineToolTargetId() {
        return commandLineToolTargetId;
    }

    public void setCommandLineToolTargetId(String commandLineToolTargetId) {
        this.commandLineToolTargetId = commandLineToolTargetId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public Long getSourceWorkflowNodeId() {
        return SourceWorkflowNodeId;
    }

    public void setSourceWorkflowNodeId(Long sourceWorkflowNodeId) {
        SourceWorkflowNodeId = sourceWorkflowNodeId;
    }

    public Long getTargetWorkflowNodeId() {
        return targetWorkflowNodeId;
    }

    public void setTargetWorkflowNodeId(Long targetWorkflowNodeId) {
        this.targetWorkflowNodeId = targetWorkflowNodeId;
    }

    public String getSourceNodeName() {
        return sourceNodeName;
    }

    public void setSourceNodeName(String sourceNodeName) {
        this.sourceNodeName = sourceNodeName;
    }

    public String getTargetNodeName() {
        return targetNodeName;
    }

    public void setTargetNodeName(String targetNodeName) {
        this.targetNodeName = targetNodeName;
    }
}
