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

package ro.cs.tao.snap.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import ro.cs.tao.component.*;
import ro.cs.tao.component.enums.ParameterType;
import ro.cs.tao.eodata.enums.DataFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OperatorHandler extends DefaultHandler {
    private StringBuilder buffer = new StringBuilder(512);
    private List<ParameterDescriptor> parameters;
    private ProcessingComponent result;
    private Logger logger;

    public OperatorHandler() {
        super();
    }

    public ProcessingComponent getResult() {
        return this.result;
    }

    @Override
    public void startDocument() throws SAXException {
        this.logger = Logger.getLogger(OperatorHandler.class.getName());
        this.result = new ProcessingComponent();
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
                    DataDescriptor targetData = new DataDescriptor();
                    targetData.setFormatType(DataFormat.RASTER);
                    targetData.setLocation("output_" + this.result.getId() + ".tif");
                    targetDescriptor.setDataDescriptor(targetData);
                    this.result.addTarget(targetDescriptor);
                    break;
                case "parameters":
                    this.result.setParameterDescriptors(this.parameters);
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
                    sourceData = new DataDescriptor();
                    sourceData.setFormatType(DataFormat.RASTER);
                    multiSources.setCardinality(0);
                    multiSources.setDataDescriptor(sourceData);
                    this.result.addSource(multiSources);
                    break;
                default:
                    ParameterDescriptor parameter = null;
                    switch (value) {
                        case "int":
                        case "integer":
                            parameter = newParameter(qName, "P" + qName, Integer.class, qName);
                            break;
                        case "boolean":
                            parameter = newParameter(qName, "P" + qName, Boolean.class, qName);
                            break;
                        case "float":
                            parameter = newParameter(qName, "P" + qName, Float.class, qName);
                            break;
                        case "string":
                        default:
                            if (value.contains(",")) {
                                switch (value.split(",")[0]) {
                                    case "int":
                                    case "integer":
                                        parameter = newParameter(qName, "P" + qName, Integer[].class, qName);
                                        break;
                                    case "boolean":
                                        parameter = newParameter(qName, "P" + qName, Boolean[].class, qName);
                                        break;
                                    case "float":
                                        parameter = newParameter(qName, "P" + qName, Float[].class, qName);
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

    @SafeVarargs
    private final <T> ParameterDescriptor newParameter(String name, String label, Class<T> clazz, String description, T... values) {
        ParameterDescriptor ret = new ParameterDescriptor(name);
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
