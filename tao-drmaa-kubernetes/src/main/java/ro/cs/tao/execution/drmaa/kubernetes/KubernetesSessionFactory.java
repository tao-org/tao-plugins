package ro.cs.tao.execution.drmaa.kubernetes;

import org.ggf.drmaa.Session;
import ro.cs.tao.drmaa.Environment;
import ro.cs.tao.execution.drmaa.AbstractSessionFactory;

public class KubernetesSessionFactory extends AbstractSessionFactory {

    public KubernetesSessionFactory() {
        super();
    }

    @Override
    protected void initLibrary() {
        // NO-OP
    }

    @Override
    public Environment getEnvironment() {
        return Environment.KUBERNETES;
    }

    @Override
    protected Session createSession() {
        return new KubernetesSession();
    }

    @Override
    protected String getJniLibraryName() {
        return null;
    }
}
