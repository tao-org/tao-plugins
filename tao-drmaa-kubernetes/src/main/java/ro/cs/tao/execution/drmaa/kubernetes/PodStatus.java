package ro.cs.tao.execution.drmaa.kubernetes;

import ro.cs.tao.TaoEnum;

public enum PodStatus implements TaoEnum<Integer> {
    UNKNOWN(0, "Unknown"),
    PENDING(1, "Pending"),
    RUNNING(2, "Running"),
    SUCCEEDED(3, "Succeeded"),
    FAILED(4, "Failed");

    private final int value;
    private final String description;

    PodStatus(int value, String description) {
        this.value = value;
        this.description = description;
    }

    @Override
    public String friendlyName() {
        return this.description;
    }

    @Override
    public Integer value() {
        return this.value;
    }
}
