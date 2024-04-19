package ro.cs.tao.snap.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.eodata.enums.Visibility;
import ro.cs.tao.persistence.PersistenceException;
import ro.cs.tao.persistence.ProcessingComponentProvider;
import ro.cs.tao.security.SessionStore;
import ro.cs.tao.services.interfaces.WorkflowService;
import ro.cs.tao.workflow.ParameterValue;
import ro.cs.tao.workflow.WorkflowDescriptor;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;
import ro.cs.tao.workflow.enums.ComponentType;
import ro.cs.tao.workflow.enums.Status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphHandler extends AbstractHandler<WorkflowDescriptor> {
    private WorkflowNodeDescriptor currentNode;
    private List<ParameterValue> currentParameters;
    private Map<String, WorkflowNodeDescriptor> nodeMap = new HashMap<>();
    private List<String[]> links = new ArrayList<>();
    boolean ignoreSection;

    GraphHandler(ProcessingComponentProvider processingComponentProvider,
                 WorkflowService workflowService) {
        super(processingComponentProvider, workflowService);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        // strip any namespace prefix
        if (qName.indexOf(":") > 0) {
            qName = qName.substring(qName.indexOf(":") + 1);
        }
        buffer.setLength(0);
        switch (qName) {
            case "graph":
                this.result.setName(attributes.getValue("id"));
                this.result.setVisibility(Visibility.PRIVATE);
                this.result.setStatus(Status.DRAFT);
                this.result.setCreated(LocalDateTime.now());
                this.result.setActive(true);
                this.result.setUserId(SessionStore.currentContext().getPrincipal().getName());
                this.result.setVisibility(Visibility.PRIVATE);
                this.result = workflowService.save(this.result);
                break;
            case "node":
                this.currentNode = new WorkflowNodeDescriptor();
                this.currentNode.setName(attributes.getValue("id"));
                break;
            case "parameters":
                this.currentParameters = new ArrayList<>();
                break;
            case "sourceProduct":
            case "source":
                String refId = attributes.getValue("refid");
                if (refId != null && !refId.startsWith("$")) {
                    links.add(new String[] { refId, currentNode.getName() });
                }
                break;
            case "applicationData":
                ignoreSection = true;
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String value = null;
        if (buffer.length() > 0 && buffer.charAt(0) != '\n') {
            value = buffer.toString();
        }
        try {
            switch (qName) {
                case "version":
                case "source":
                case "sources":
                case "sourceProduct":
                    break;
                case "graph":
                    for (String[] pair : links) {
                        ProcessingComponent sourceComponent = processingComponentProvider.get(nodeMap.get(pair[0]).getComponentId());
                        ProcessingComponent targetComponent = processingComponentProvider.get(nodeMap.get(pair[1]).getComponentId());
                        currentNode = workflowService.addLink(nodeMap.get(pair[0]).getId(),
                                                              sourceComponent.getTargets().get(0).getId(),
                                                              nodeMap.get(pair[1]).getId(),
                                                              targetComponent.getSources().get(0).getId());
                    }
                    break;
                case "node":
                    if (!ignoreSection) {
                        currentNode = workflowService.addNode(this.result.getId(), currentNode);
                        nodeMap.put(currentNode.getName(), currentNode);
                        currentNode = null;
                    }
                    break;
                case "operator":
                    if (!ignoreSection && value != null) {
                        currentNode.setComponentId("snap-" + value.toLowerCase());
                        currentNode.setComponentType(ComponentType.PROCESSING);
                    }
                    break;
                case "parameters":
                    if (!ignoreSection) {
                        this.currentNode.setCustomValues(this.currentParameters);
                        this.currentParameters = null;
                    }
                    break;
                case "applicationData":
                    ignoreSection = false;
                    break;
                default: // must be a parameter tag
                    if (!ignoreSection) {
                        this.currentParameters.add(new ParameterValue(qName, value));
                    }
                    break;
            }
        } catch (PersistenceException pex) {
            throw new SAXException(pex);
        }
    }

    @Override
    protected Class<WorkflowDescriptor> resultClass() { return WorkflowDescriptor.class; }
}
