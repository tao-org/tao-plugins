package ro.cs.tao.execution.drmaa.simpipe;

import org.ggf.drmaa.Session;
import ro.cs.tao.drmaa.Environment;
import ro.cs.tao.execution.drmaa.AbstractSessionFactory;

public class SimpipeSessionFactory extends AbstractSessionFactory {

    /**
     * Creates a new instance of AbstractSessionFactory.
     */

    public SimpipeSessionFactory() {
        super();
    }

    @Override
    public Environment getEnvironment() {
        return Environment.SIMPIPE;
    }

    @Override
    protected Session createSession() {
        return new SimpipeSession();
    }

    @Override
    protected String getJniLibraryName() {
        return null;
    }
}
