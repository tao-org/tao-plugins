package ro.cs.tao.snap.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.eodata.enums.Visibility;
import ro.cs.tao.persistence.PersistenceManager;
import ro.cs.tao.persistence.exception.PersistenceException;
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
    private List<ParameterValue> currentParameters = new ArrayList<>();
    private Map<String, WorkflowNodeDescriptor> nodeMap = new HashMap<>();

    public GraphHandler(PersistenceManager persistenceManager, WorkflowService workflowService) {
        super(persistenceManager, workflowService);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // strip any namespace prefix
        if (qName.indexOf(":") > 0) {
            qName = qName.substring(qName.indexOf(":") + 1);
        }
        buffer.setLength(0);
        try {
            switch (qName) {
                case "graph":
                    this.result.setName(attributes.getValue("id"));
                    this.result.setVisibility(Visibility.PRIVATE);
                    this.result.setStatus(Status.DRAFT);
                    this.result.setCreated(LocalDateTime.now());
                    this.result.setActive(true);
                    this.result.setUserName(SessionStore.currentContext().getPrincipal().getName());
                    this.result.setVisibility(Visibility.PRIVATE);
                    this.result = persistenceManager.saveWorkflowDescriptor(this.result);
                    break;
                case "node":
                    this.currentNode = new WorkflowNodeDescriptor();
                    this.currentNode.setName(attributes.getValue("id"));
                    break;
                case "sourceProduct":
                case "source":
                    String refId = attributes.getValue("refid");
                    if (refId != null && !refId.startsWith("$")) {
                        List<ProcessingComponent> components = persistenceManager.getProcessingComponentByLabel(refId);
                        if (components == null || components.size() != 1) {
                            throw new PersistenceException(String.format("Multiple components found with label '%s'",
                                                                         refId));
                        }
                        ProcessingComponent sourceComponent = components.get(0);
                        components = persistenceManager.getProcessingComponentByLabel(currentNode.getComponentId());
                        if (components == null || components.size() != 1) {
                            throw new PersistenceException(String.format("Multiple components found with label '%s'",
                                                                         currentNode.getComponentId()));
                        }
                        ProcessingComponent currentComponent = components.get(0);
                        currentNode = workflowService.addLink(nodeMap.get(refId).getId(),
                                                              sourceComponent.getTargets().get(0).getId(),
                                                              currentNode.getId(),
                                                              currentComponent.getSources().get(0).getId());
                    }
                    break;
            }
        } catch (PersistenceException pex) {
            throw new SAXException(pex);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (buffer.length() > 0 && buffer.charAt(0) != '\n') {
            String value = buffer.toString();
            try {
                switch (qName) {
                    case "graph":
                    case "version":
                    case "source":
                    case "sources":
                    case "sourceProduct":
                        break;
                    case "node":
                        currentNode = this.workflowService.addNode(this.result.getId(), currentNode);
                        nodeMap.put(currentNode.getComponentId(), currentNode);
                        currentNode = null;
                        break;
                    case "operator":
                        currentNode.setComponentId(value);
                        currentNode.setComponentType(ComponentType.PROCESSING);
                        break;
                    case "parameters":
                        this.result.setCustomValues(this.currentParameters);
                        break;
                    default: // must be a parameter tag
                        this.currentParameters.add(new ParameterValue(qName, value));
                        break;
                }
            } catch (PersistenceException pex) {
                throw new SAXException(pex);
            }
        }
    }

    @Override
    protected Class<WorkflowDescriptor> resultClass() { return WorkflowDescriptor.class; }
}
