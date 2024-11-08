/*
 * Copyright (C) 2018 CS ROMANIA
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
import ro.cs.tao.component.SourceDescriptor;
import ro.cs.tao.snap.xml.OperatorParser;
import ro.cs.tao.workflow.WorkflowNodeDescriptor;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static String toSnapXml(ProcessingComponent component, String parentId) {
        if (component == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        final String sourceTemplate = component.getTemplateContents();
        builder.append("\t\t\t<node id=\"").append(component.getId()).append("\">\n");
        final String operatorId = sourceTemplate != null ? sourceTemplate.substring(0, sourceTemplate.indexOf('\n')) : null;
        if (operatorId == null) {
            throw new IllegalArgumentException("Cannot determine SNAP operator from component " + component.getId());
        }
        builder.append("\t\t\t\t<operator>").append(operatorId).append("</operator>\n");
        if ("Read".equals(operatorId)) {
            builder.append("\t\t\t\t<sources/>\n");
        } else {
            builder.append("\t\t\t\t<sources>\n");
            final List<SourceDescriptor> sourceDescriptors = component.getSources();
            if (sourceDescriptors != null) {
                if (parentId != null) {
                    builder.append("\t\t\t\t\t<sourceProduct refid=\"").append(parentId).append("\"/>\n");
                } else {
                    final int size = sourceDescriptors.size();
                    String sourceName;
                    if (size == 1) {
                        SourceDescriptor descriptor = sourceDescriptors.get(0);
                        sourceName = descriptor.getName();
                        sourceName = sourceName.substring(1);
                        builder.append("\t\t\t\t\t<sourceProduct refid=\"$[").append(sourceName).append("]\" />\n");
                        if (descriptor.getCardinality() > 1) {
                            for (int i = 1; i < descriptor.getCardinality(); i++) {
                                builder.append("\t\t\t\t\t<sourceProduct refid=\"$[").append(sourceName)
                                       .append(".").append(i + 1).append("]\"/>\n");
                            }
                        }
                    } else {
                        for (SourceDescriptor sourceDescriptor : sourceDescriptors) {
                            sourceName = sourceDescriptor.getName();
                            sourceName = sourceName.substring(1);
                            builder.append("\t\t\t\t\t<sourceProduct refid=\"$[").append(sourceName).append("]\"/>\n");
                        }
                    }
                }
            }
            builder.append("\t\t\t\t</sources>\n");
        }
        builder.append("\t\t\t\t<parameters class=\"com.bc.ceres.binding.dom.XppDomElement\">\n");
        Set<ParameterDescriptor> parameters = component.getParameterDescriptors();
        if (parameters != null) {
            for (ParameterDescriptor parameter : parameters) {
                builder.append(parameterToXml(component.getId(), parameter));
            }
        }
        builder.append("\t\t\t\t</parameters>\n");
        builder.append("\t\t\t</node>\n");
        return builder.toString();
    }
    /**
     * Converts a list of TAO Processing Components which corresponds to several SNAP operators into the respective SNAP Graph XML
     *
     * @param components  The TAO processing components
     */
    static String toSnapXml(List<ProcessingComponent> components,
                            Map<String, Map<String, String>> values) {
        if (components == null || components.size() == 0) {
            return null;
        }
        if (components.size() == 1) {
            return toSnapXml(components.get(0), null);
        } else {
            final StringBuilder builder = new StringBuilder();
            builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    .append("<xsl:stylesheet version=\"1.0\"\n")
                    .append("xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n")
                    .append("\t<xsl:output method=\"xml\" indent=\"yes\" encoding=\"UTF-8\"/>\n");
            for (ProcessingComponent component : components) {
                Set<ParameterDescriptor> descriptors = component.getParameterDescriptors();
                final Map<String, String> overwrittenValues = values.get(component.getId());
                for (ParameterDescriptor descriptor : descriptors) {
                    builder.append(parameterToXslParam(component.getId(), descriptor, overwrittenValues.get(descriptor.getId())));
                }
            }
            builder.append("\t<xsl:template match=\"/\">\n");
            builder.append("\t\t<graph id=\"snap_aggregated_operators\">\n")
                    .append("\t\t\t<version>1.0</version>\n");
            builder.append(toSnapXml(components.get(0), null));
            for (int i = 1; i < components.size(); i++) {
                builder.append(toSnapXml(components.get(i), components.get(i-1).getId()));
            }
            builder.append("\t\t</graph>\n");
            builder.append("\t</xsl:template>\n").append("</xsl:stylesheet>");
            return builder.toString();
        }
    }

    private static String parameterToXml(String componentId, ParameterDescriptor parameter) {
        return "\t\t\t\t\t<" + parameter.getName() + ">" +
                "<xsl:value-of select=\"$" +
                componentId + "-" + parameter.getName() +
                "\"/>" +
                "</" + parameter.getName() + ">\n";
    }

    private static String parameterToXslParam(String componentId, ParameterDescriptor parameter, String value) {
        StringBuilder builder = new StringBuilder();
        builder.append("\t<xsl:param name=\"").append(componentId).append("-").append(parameter.getName()).append("\" ");
        String val = value == null ? parameter.getDefaultValue() : value;
        if (val != null) {
            if ((val.startsWith("'") && val.endsWith("'")) || (val.startsWith("\"") && val.endsWith("\""))) {
                val = val.substring(1, val.length() - 1);
            }
            builder.append("select=\"").append(val).append("\" ");
        }
        builder.append("/>\n");
        return builder.toString();
    }
}
