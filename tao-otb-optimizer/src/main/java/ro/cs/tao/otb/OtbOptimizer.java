package ro.cs.tao.otb;

import ro.cs.tao.component.AggregationException;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.component.RuntimeOptimizer;
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.services.bridge.spring.SpringContextBridge;

public class OtbOptimizer implements RuntimeOptimizer {
    private static final PersistenceManager persistenceManager;

    static {
        persistenceManager = SpringContextBridge.services().getService(PersistenceManager.class);
    }

    @Override
    public boolean isIntendedFor(String containerId) {
        return containerId != null && persistenceManager.getContainerById(containerId).getName().toLowerCase().contains("otb");
    }

    @Override
    public ProcessingComponent aggregate(ProcessingComponent... sources) throws AggregationException {
        return null;
    }
}
