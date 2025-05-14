package ro.cs.tao.topology.openstack.commons;

/**
 * The PlatformUsage class represents the resource usage of a computing platform.
 * It encapsulates the maximum and used quantities of CPUs, memory, and storage resources.
 *
 * This class is immutable, and its fields are initialized via the constructor.
 * The class provides getter methods to access the values for maximum and used resources.
 */
public class PlatformUsage {
    private final int maxCpus;
    private final int usedCpus;
    private final int maxMemory;
    private final int usedMemory;
    private final int maxStorage;
    private final int usedStorage;

    public PlatformUsage(int maxCpus, int usedCpus, int maxMemory, int usedMemory, int maxStorage, int usedStorage) {
        this.maxCpus = maxCpus;
        this.usedCpus = usedCpus;
        this.maxMemory = maxMemory;
        this.usedMemory = usedMemory;
        this.maxStorage = maxStorage;
        this.usedStorage = usedStorage;
    }

    public int getMaxCpus() {
        return maxCpus;
    }

    public int getMaxMemory() {
        return maxMemory;
    }

    public int getMaxStorage() {
        return maxStorage;
    }

    public int getUsedCpus() {
        return usedCpus;
    }

    public int getUsedMemory() {
        return usedMemory;
    }

    public int getUsedStorage() {
        return usedStorage;
    }
}
