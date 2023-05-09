package ro.cs.tao.sen2agri.model;

import org.json.simple.JSONObject;

public class TaskMessage {
    private int externalTaskId;
    private String stepName;
    private int stepIndex;
    private String messageType;
    private String status;
    private String stdOutText;
    private String stdErrText;
    private int exitCode;
    private long execTime;

    public static TaskMessage createStartMessage(int taskId, String step, int stepIndex) {
        return new TaskMessage() {{
            setMessageType("STARTED");
            setExternalTaskId(taskId);
            setStepName(step);
            setStepIndex(stepIndex);
        }};
    }

    public static TaskMessage createProgressMessage(int taskId, String step, int stepIndex, long time) {
        return new TaskMessage() {{
            setMessageType("STARTED");
            setExternalTaskId(taskId);
            setStepName(step);
            setStepIndex(stepIndex);
            setExecTime(time);
        }};
    }

    public static TaskMessage createEndMessage(int taskId, String step, int stepIndex, String output, int exitCode, long time) {
        return new TaskMessage() {{
            setMessageType("ENDED");
            setStatus("OK");
            setExternalTaskId(taskId);
            setStepName(step);
            setStepIndex(stepIndex);
            setExitCode(exitCode);
            setStdOutText(output);
            setExecTime(time);
        }};
    }

    public static TaskMessage createErrorMessage(int taskId, String step, int stepIndex, String error, String output, int exitCode, long time) {
        return new TaskMessage() {{
            setMessageType("ENDED");
            setStatus("FAILED");
            setExternalTaskId(taskId);
            setStepName(step);
            setStepIndex(stepIndex);
            setExitCode(exitCode);
            setStdOutText(output);
            setStdErrText(output);
            setExecTime(time);
        }};
    }

    public TaskMessage() {
    }

    public int getExternalTaskId() {
        return externalTaskId;
    }

    public void setExternalTaskId(int externalTaskId) {
        this.externalTaskId = externalTaskId;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStdOutText() {
        return stdOutText;
    }

    public void setStdOutText(String stdOutText) {
        this.stdOutText = stdOutText;
    }

    public String getStdErrText() {
        return stdErrText;
    }

    public void setStdErrText(String stdErrText) {
        this.stdErrText = stdErrText;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public long getExecTime() {
        return execTime;
    }

    public void setExecTime(long execTime) {
        this.execTime = execTime;
    }

    @Override
    public String toString() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("JOB_NAME", "TSKID_" + externalTaskId + "_STEPNAME_" + stepName + "_" + stepIndex);
        jsonObject.put("MSG_TYPE", messageType);
        if (!"STARTED".equals(messageType)) {
            jsonObject.put("EXEC_TIME", execTime);
            jsonObject.put("EXIT_CODE", exitCode);
            jsonObject.put("STATUS", status);
            jsonObject.put("STDOUT_TEXT", stdOutText);
            jsonObject.put("STDERR_TEXT", stdErrText);
        }
        return jsonObject.toJSONString();
    }
}
