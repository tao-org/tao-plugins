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
    private static final String TAB_1 = "\t";
    private static final String TAB_2 = "\t\t";
    private static final String TAB_3 = "\t\t\t";
    private static final String TAB_4 = "\t\t\t\t";
    private static final String TAB_5 = "\t\t\t\t\t";
    private static final String parameterXml = TAB_5 + "<%1$s><xsl:value-of select=\"$%2$s-%1$s\"/></%1$s>\n";
    private static final String parameterXsl = TAB_1 + "<xsl:variable name=\"%s-%s\">%s</xsl:variable>";
    private static final String templateHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                 "<xsl:stylesheet version=\"1.0\"\n" +
                                                 "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n" +
                                                 TAB_1 + "<xsl:output method=\"xml\" indent=\"yes\" encoding=\"UTF-8\"/>\n";
    private static final String templateStart = TAB_1 + "<xsl:template match=\"/\">\n" +
                                                TAB_2 + "<graph id=\"snap_aggregated_operators\">\n" +
                                                TAB_3 + "<version>1.0</version>\n";
    private static final String templateFooter = TAB_2 + "</graph>\n" +
                                                 TAB_1 + "</xsl:template>\n</xsl:stylesheet>";

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
        final StringBuilder builder = new StringBuilder();
        final String sourceTemplate = component.getTemplateContents();
        builder.append(TAB_3).append("<node id=\"").append(component.getId()).append("\">\n");
        final String operatorId = sourceTemplate != null ? sourceTemplate.substring(0, sourceTemplate.indexOf('\n')) : null;
        if (operatorId == null) {
            throw new IllegalArgumentException("Cannot determine SNAP operator from component " + component.getId());
        }
        builder.append(TAB_4).append("<operator>").append(operatorId).append("</operator>\n");
        if ("Read".equals(operatorId)) {
            builder.append(TAB_4).append("<sources/>\n");
        } else {
            builder.append(TAB_4).append("<sources>\n");
            final List<SourceDescriptor> sourceDescriptors = component.getSources();
            if (sourceDescriptors != null) {
                if (parentId != null) {
                    builder.append(TAB_5).append("<sourceProduct refid=\"").append(parentId).append("\"/>\n");
                } else {
                    final int size = sourceDescriptors.size();
                    String sourceName;
                    if (size == 1) {
                        SourceDescriptor descriptor = sourceDescriptors.get(0);
                        sourceName = descriptor.getName();
                        sourceName = sourceName.substring(1);
                        builder.append(TAB_5).append("<sourceProduct refid=\"$[").append(sourceName).append("]\" />\n");
                        if (descriptor.getCardinality() > 1) {
                            for (int i = 1; i < descriptor.getCardinality(); i++) {
                                builder.append(TAB_5).append("<sourceProduct refid=\"$[").append(sourceName)
                                       .append(".").append(i + 1).append("]\"/>\n");
                            }
                        }
                    } else {
                        for (SourceDescriptor sourceDescriptor : sourceDescriptors) {
                            sourceName = sourceDescriptor.getName();
                            sourceName = sourceName.substring(1);
                            builder.append(TAB_5).append("<sourceProduct refid=\"$[").append(sourceName).append("]\"/>\n");
                        }
                    }
                }
            }
            builder.append(TAB_4).append("</sources>\n");
        }
        builder.append(TAB_4).append("<parameters class=\"com.bc.ceres.binding.dom.XppDomElement\">\n");
        Set<ParameterDescriptor> parameters = component.getParameterDescriptors();
        if (parameters != null) {
            for (ParameterDescriptor parameter : parameters) {
                builder.append(parameterToXml(component.getId(), parameter));
            }
        }
        builder.append(TAB_4).append("</parameters>\n");
        builder.append(TAB_3).append("</node>\n");
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
            builder.append(templateHeader);
            for (ProcessingComponent component : components) {
                Set<ParameterDescriptor> descriptors = component.getParameterDescriptors();
                final Map<String, String> overwrittenValues = values.get(component.getId());
                for (ParameterDescriptor descriptor : descriptors) {
                    builder.append(parameterToXslParam(component.getId(), descriptor, overwrittenValues.get(descriptor.getId())));
                }
            }
            builder.append(templateStart);
            builder.append(toSnapXml(components.get(0), null));
            for (int i = 1; i < components.size(); i++) {
                builder.append(toSnapXml(components.get(i), components.get(i-1).getId()));
            }
            builder.append(templateFooter);
            return builder.toString();
        }
    }

    /**
     * Converts a parameter's information into its XML representation.
     *
     * @param componentId the identifier of the component to which the parameter belongs
     * @param parameter the descriptor of the parameter to be converted to XML
     * @return the XML representation of the parameter as a string
     */
    private static String parameterToXml(String componentId, ParameterDescriptor parameter) {
        return String.format(parameterXml, parameter.getName(), componentId);
    }

    /**
     * Converts a parameter's information into its XSL parameter representation.
     *
     * @param componentId the identifier of the component to which the parameter belongs
     * @param parameter the descriptor of the parameter to be converted to an XSL parameter
     * @param value the specific value of the parameter; if null, the parameter's default value will be used
     * @return the XSL parameter representation of the specified parameter as a string
     */
    private static String parameterToXslParam(String componentId, ParameterDescriptor parameter, String value) {
        String val = value == null ? parameter.getDefaultValue() : value;
        if (val != null) {
            final int length = val.length() - 1;
            if ((val.charAt(0) == '\'' && val.charAt(length) == '\'') ||
                (val.charAt(0) == '\"' && val.charAt(length) == '\"') ||
                (val.charAt(0) == '{' && val.charAt(length) == '}') ||
                (val.charAt(0) == '[' && val.charAt(length) == ']')) {
                val = val.substring(1, length);
            }
        } else {
            val = "";
        }
        return String.format(parameterXsl, componentId, parameter.getName(), val);
    }
}
