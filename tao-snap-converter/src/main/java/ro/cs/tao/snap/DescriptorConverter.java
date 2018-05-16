/*
 * Copyright (C) 2017 CS ROMANIA
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package ro.cs.tao.snap;

import org.xml.sax.SAXException;
import ro.cs.tao.component.ParameterDescriptor;
import ro.cs.tao.component.ProcessingComponent;
import ro.cs.tao.snap.xml.OperatorParser;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;

/**
 * Utility class that performs bidirectional conversion between TAO nodes and SNAP operator XML descriptors.
 *
 * @author Cosmin Cara
 */
public class DescriptorConverter {
    /**
     * Converts a SNAP Graph into a collection of linked TAO workflow nodes
     *
     * @param graphXml The graph XML
     */
    public static List<WorkflowNodeDescriptor> fromSnapGraphXml(String graphXml) {
        return null;
    }
    /**
     * Converts a collection of linked TAO workflow nodes into a SNAP Graph.
     * The nodes have to be linked and all of them should have been created from SNAP processing components
     *
     * @param nodes     The collection of nodes
     */
    public static String toSnapGraphXml(List<WorkflowNodeDescriptor> nodes) {
        return null;
    }
    /**
     * Converts a SNAP operator XML into a TAO Processing Component
     *
     * @param operatorXml   The operator XML
     */
    public static ProcessingComponent fromXml(String operatorXml) throws IOException, SAXException, ParserConfigurationException {
        return OperatorParser.parse(operatorXml);
        /*WorkflowNodeDescriptor node = new WorkflowNodeDescriptor();
        node.setName(processingComponent.getId().toUpperCase());
        node.setxCoord(300);
        node.setyCoord(500);
        node.setComponentId(processingComponent.getId());
        node.setComponentType(ComponentType.PROCESSING);
        node.setCreated(LocalDateTime.now());
        return node;*/
    }
    /**
     * Converts a TAO Processing Component which corresponds to a SNAP operator into the respective's operator SNAP XML
     *
     * @param component  The TAO processing component
     */
    public static String toSnapXml(ProcessingComponent component) {
        if (component == null || component.getContainerId() == null
                || !component.getContainerId().contains("snap") || !component.getContainerId().contains("SNAP")) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<node id=\"").append(component.getId()).append("\">\n");
        builder.append("<operator>").append(component.getId()).append("</operator>\n");
        builder.append("<sources>\n");
        builder.append("</sources>\n");
        builder.append("<parameters class=\"com.bc.ceres.binding.dom.XppDomElement\">\n");
        List<ParameterDescriptor> parameters = component.getParameterDescriptors();
        if (parameters != null) {
            for (ParameterDescriptor parameter : parameters) {
                builder.append("<").append(parameter.getId()).append(">")
                        .append(parameter.getDataType().getSimpleName().toLowerCase())
                        .append("</").append(parameter.getId()).append(">\n");
            }
        }
        builder.append("</parameters>\n");
        builder.append("</node>\n");
        return builder.toString();
    }

}
