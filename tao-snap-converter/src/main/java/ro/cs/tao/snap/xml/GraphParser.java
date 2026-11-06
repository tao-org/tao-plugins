package ro.cs.tao.snap.xml;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import ro.cs.tao.persistence.ProcessingComponentProvider;
import ro.cs.tao.services.interfaces.WorkflowService;
import ro.cs.tao.workflow.WorkflowDescriptor;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;

/**
 * Utility class responsible for parsing XML strings into workflow graph representations.
 */
public class GraphParser {

    /**
     * Parses an XML string representation of a workflow graph into a {@code WorkflowDescriptor} object.
     *
     * @param componentProvider the provider responsible for supplying instances of processing components,
     *                          used during the parsing of the workflow graph.
     * @param workflowService   the service responsible for workflow-related operations,
     *                          utilized during the parsing process.
     * @param xmlString         the XML string representing the workflow graph that needs to be parsed.
     * @return a {@code WorkflowDescriptor} object that represents the parsed workflow graph.
     * @throws ParserConfigurationException if a serious configuration error is detected while
     *                                      configuring the XML parser.
     * @throws SAXException                 if an error occurs during parsing of the XML input.
     * @throws IOException                  if an input-output error occurs while processing the XML input.
     */
    public static WorkflowDescriptor parse(ProcessingComponentProvider componentProvider,
                                           WorkflowService workflowService, String xmlString)
            throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        GraphHandler handler = new GraphHandler(componentProvider, workflowService);
        parser.parse(new InputSource(new StringReader(xmlString)), handler);
        return handler.getResult();
    }
}
