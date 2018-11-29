package ro.cs.tao.snap.xml;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.services.interfaces.WorkflowService;

import java.util.logging.Logger;

public abstract class AbstractHandler<T> extends DefaultHandler {
    protected final PersistenceManager persistenceManager;
    protected final WorkflowService workflowService;
    protected final StringBuilder buffer = new StringBuilder(512);
    protected final Logger logger = Logger.getLogger(getClass().getName());
    protected T result;

    public AbstractHandler(PersistenceManager persistenceManager, WorkflowService workflowService) {
        super();
        this.persistenceManager = persistenceManager;
        this.workflowService = workflowService;
    }

    public T getResult() { return result; }

    @Override
    public void startDocument() throws SAXException {
        try {
            this.result = resultClass().newInstance();
        } catch (Exception e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        //characters() may be called several times for chunks of one element by a SAX parser
        buffer.append(ch, start, length);
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        String error = e.getMessage();
        if (!error.contains("no grammar found"))
            logger.warning(e.getMessage());
    }

    protected abstract Class<T> resultClass();
}
