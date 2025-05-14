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

package ro.cs.tao.snap.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import ro.cs.tao.component.*;
import ro.cs.tao.component.enums.ParameterType;
import ro.cs.tao.component.enums.ProcessingComponentType;
import ro.cs.tao.component.enums.ProcessingComponentVisibility;
import ro.cs.tao.component.template.TemplateType;
import ro.cs.tao.eodata.enums.DataFormat;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The OperatorHandler class is responsible for parsing XML elements and transforming them into
 * relevant objects for use in processing components. It extends the DefaultHandler class, enabling
 * it to act as a SAX XML parser. OperatorHandler is utilized in the context of creating and
 * managing processing components, where XML input defines configurations, parameters, sources, and
 * targets.
 *
 * This class handles the construction and initialization of a ProcessingComponent object by
 * interpreting XML data. It uses the SAX callback methods to parse XML documents and convert the
 * parsed elements into various descriptors such as SourceDescriptor, TargetDescriptor, and
 * ParameterDescriptor.
 *
 * Features include:
 * - Parsing and creating descriptors based on element names and data.
 * - Defining a working environment for processing components.
 * - Handling errors during the parsing process and logging appropriate warnings.
 * - Automatically setting default targets when none are defined.
 *
 * Constructor Details:
 * - The no-argument constructor allows for creating an OperatorHandler without specifying a container ID.
 * - The constructor with a containerId argument allows for associating a particular container ID with
 *   the resulting ProcessingComponent.
 *
 * Overridden Methods:
 * - startDocument(): Initializes logging, creates a new ProcessingComponent, and sets several default
 *   properties for the component.
 * - endDocument(): Adds default target descriptors if none are defined in the XML input.
 * - startElement(): Prepares to process an XML element by resetting the buffer.
 * - endElement(): Processes the collected characters and modifies descriptors, parameters, or component
 *   properties based on the element name and content.
 * - characters(): Collects contiguous character data from within an element.
 * - error(SAXParseException): Logs non-critical errors during parsing.
 *
 * Additional Functionality:
 * - getResult(): Returns the fully constructed and configured ProcessingComponent object.
 * - newParameter(): A helper method for creating new ParameterDescriptor instances.
 */
public class OperatorHandler extends DefaultHandler {
    private StringBuilder buffer = new StringBuilder(512);
    private List<ParameterDescriptor> parameters;
    private ProcessingComponent result;
    private Logger logger;
    private String containerId;

    public OperatorHandler() {
        super();
    }

    public OperatorHandler(String containerId) {
        super();
        this.containerId = containerId;
    }

    /**
     * Configures and returns the final {@code ProcessingComponent} object with template type,
     * sources, parameters, targets, and template contents set up.
     *
     * The method processes a list of sources, parameters, and targets to create a template content string.
     * It updates the internal state of the {@code result} object with information extracted from these lists
     * before returning it.
     *
     * @return the fully configured {@code ProcessingComponent} object
     */
    public ProcessingComponent getResult() {
        this.result.setTemplateType(TemplateType.VELOCITY);
        final StringBuilder builder = new StringBuilder();
        final List<SourceDescriptor> sources = this.result.getSources();
        if (sources != null) {
            for (SourceDescriptor descriptor : sources) {
                builder.append("-").append(descriptor.getName()).append("=$").append(descriptor.getName()).append("\n");
            }
        }
        if (this.parameters != null) {
            this.result.setParameterDescriptors(parameters.stream().collect(Collectors.toSet()));
            for (ParameterDescriptor descriptor : this.parameters) {
                builder.append("-").append(descriptor.getLabel()).append("=$").append(descriptor.getName()).append("\n");
            }
        }
        final List<TargetDescriptor> targets = this.result.getTargets();
        if (targets != null) {
            for (TargetDescriptor descriptor : targets) {
                builder.append("-").append(descriptor.getName()).append(" $").append(descriptor.getName()).append("\n");
            }
        }
        this.result.setTemplateContents(builder.toString());
        return this.result;
    }

    @Override
    public void startDocument() throws SAXException {
        this.logger = Logger.getLogger(OperatorHandler.class.getName());
        this.result = new ProcessingComponent();
        this.result.setAuthors("SNAP Team");
        this.result.setId(UUID.randomUUID().toString());
        this.result.setContainerId(containerId);
        this.result.setNodeAffinity(NodeAffinity.Any);
        this.result.setFileLocation("gpt");
        this.result.setWorkingDirectory(".");
        this.result.setVisibility(ProcessingComponentVisibility.SYSTEM);
        this.result.setMultiThread(true);
        this.result.setParallelism(8);
        this.result.setActive(true);
        this.result.setComponentType(ProcessingComponentType.EXECUTABLE);
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        if (this.result.getTargets().isEmpty()) {
            TargetDescriptor targetDescriptor = new TargetDescriptor();
            targetDescriptor.setId(UUID.randomUUID().toString());
            targetDescriptor.setName("t");
            targetDescriptor.setParentId(this.result.getId());
            DataDescriptor targetData = new DataDescriptor();
            targetData.setFormatType(DataFormat.RASTER);
            targetData.setLocation("output_" + this.result.getId() + ".tif");
            targetDescriptor.setDataDescriptor(targetData);
            this.result.addTarget(targetDescriptor);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        //characters() may be called several times for chunks of one element by a SAX parser
        buffer.append(ch, start, length);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // strip any namespace prefix
        if (qName.indexOf(":") > 0) {
            qName = qName.substring(qName.indexOf(":") + 1);
        }
        buffer.setLength(0);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (buffer.length() > 0 && buffer.charAt(0) != '\n') {
            String value = buffer.toString();
            switch (qName) {
                case "graph":
                    TargetDescriptor targetDescriptor = new TargetDescriptor();
                    targetDescriptor.setId(UUID.randomUUID().toString());
                    targetDescriptor.setName("t");
                    targetDescriptor.setParentId(this.result.getId());
                    DataDescriptor targetData = new DataDescriptor();
                    targetData.setFormatType(DataFormat.RASTER);
                    targetData.setLocation("output_" + this.result.getId() + ".dim");
                    targetDescriptor.setDataDescriptor(targetData);
                    this.result.addTarget(targetDescriptor);
                    break;
                case "parameters":
                    this.result.setParameterDescriptors(new LinkedHashSet<>(this.parameters));
                    break;
                case "version":
                    this.result.setVersion(value);
                    break;
                case "operator":
                    this.result.setId("snap-" + value.toLowerCase());
                    this.result.setLabel(value);
                    this.result.setDescription(value + " operator");
                    break;
                case "source":
                case "sourceProduct":
                    SourceDescriptor singleSource = new SourceDescriptor();
                    singleSource.setId(UUID.randomUUID().toString());
                    singleSource.setName("S" + qName);
                    singleSource.setParentId(this.result.getId());
                    DataDescriptor sourceData = new DataDescriptor();
                    sourceData.setFormatType(DataFormat.RASTER);
                    singleSource.setDataDescriptor(sourceData);
                    singleSource.setCardinality(1);
                    this.result.addSource(singleSource);
                    break;
                case "sourceProducts":
                    SourceDescriptor multiSources = new SourceDescriptor();
                    multiSources.setId(UUID.randomUUID().toString());
                    multiSources.setName("S" + qName);
                    multiSources.setParentId(this.result.getId());
                    sourceData = new DataDescriptor();
                    sourceData.setFormatType(DataFormat.RASTER);
                    multiSources.setCardinality(0);
                    multiSources.setDataDescriptor(sourceData);
                    this.result.addSource(multiSources);
                    break;
                case "sources":
                    break;
                default:
                    ParameterDescriptor parameter = null;
                    switch (value) {
                        case "int":
                        case "integer":
                            parameter = newParameter(qName, "P" + qName, int.class, qName);
                            break;
                        case "boolean":
                            parameter = newParameter(qName, "P" + qName, boolean.class, qName);
                            break;
                        case "float":
                            parameter = newParameter(qName, "P" + qName, float.class, qName);
                            break;
                        case "double":
                            parameter = newParameter(qName, "P" + qName, double.class, qName);
                            break;
                        case "string":
                        default:
                            if (value.contains(",")) {
                                switch (value.split(",")[0]) {
                                    case "int":
                                    case "integer":
                                        parameter = newParameter(qName, "P" + qName, int[].class, qName);
                                        break;
                                    case "boolean":
                                        parameter = newParameter(qName, "P" + qName, boolean[].class, qName);
                                        break;
                                    case "float":
                                        parameter = newParameter(qName, "P" + qName, float[].class, qName);
                                        break;
                                    case "double":
                                        parameter = newParameter(qName, "P" + qName, double[].class, qName);
                                        break;
                                    case "string":
                                    default:
                                        parameter = newParameter(qName, "P" + qName, String[].class, qName);
                                        break;
                                }
                            } else {
                                parameter = newParameter(qName, "P" + qName, String.class, qName);
                            }
                            break;
                    }
                    if (parameter != null) {
                        if (this.parameters == null) {
                            this.parameters = new ArrayList<>();
                        }
                        this.parameters.add(parameter);
                    }
                    buffer.setLength(0);
                break;
            }
        }
    }

    @Override
    public void error(SAXParseException e) throws SAXException {
        String error = e.getMessage();
        if (!error.contains("no grammar found"))
            logger.warning(e.getMessage());
    }

    /**
     * Creates a new {@code ParameterDescriptor} object with the specified properties.
     *
     * @param <T>       the type of the parameter values
     * @param name      the name of the parameter
     * @param label     the display label of the parameter
     * @param clazz     the class representing the type of the parameter
     * @param description a description of the parameter
     * @param values    optional predefined values for the parameter
     * @return the created {@code ParameterDescriptor} with the specified attributes
     */
    @SafeVarargs
    private final <T> ParameterDescriptor newParameter(String name, String label, Class<T> clazz, String description, T... values) {
        ParameterDescriptor ret = new ParameterDescriptor(name);
        ret.setName(name);
        ret.setType(ParameterType.REGULAR);
        ret.setDataType(clazz);
        ret.setDescription(description);
        ret.setLabel(label);
        if (values != null) {
            String[] set = new String[values.length];
            Arrays.stream(values).map(String::valueOf).collect(Collectors.toList()).toArray(set);
            ret.setValueSet(set);
        }
        return ret;
    }
}
