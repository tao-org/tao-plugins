package ro.cs.tao.snap.xml;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.services.interfaces.WorkflowService;
import ro.cs.tao.workflow.WorkflowDescriptor;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;

public class GraphParser {

    public static WorkflowDescriptor parse(PersistenceManager persistenceManager,
                                           WorkflowService workflowService, String xmlString)
            throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        GraphHandler handler = new GraphHandler(persistenceManager, workflowService);
        parser.parse(new InputSource(new StringReader(xmlString)), handler);
        return handler.getResult();
    }
}
