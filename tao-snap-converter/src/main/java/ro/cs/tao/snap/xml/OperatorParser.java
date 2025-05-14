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

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import ro.cs.tao.component.ProcessingComponent;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.StringReader;

/**
 * The OperatorParser class provides functionality to parse XML strings into
 * ProcessingComponent instances using SAX parsing. It supports optional
 * initialization with a container ID to customize parsing behavior.
 */
public class OperatorParser {
    private final String containerId;

    public OperatorParser() {
        this.containerId = null;
    }

    public OperatorParser(String containerId) {
        this.containerId = containerId;
    }

    /**
     * Parses the given XML string and converts it into a ProcessingComponent instance.
     *
     * @param xmlString the XML string to be parsed
     * @return a ProcessingComponent instance created from the parsed XML string
     * @throws ParserConfigurationException if a document builder cannot be created
     * @throws SAXException if any parsing errors occur
     * @throws IOException if an I/O error occurs during reading the XML string
     */
    public static ProcessingComponent parse(String xmlString) throws ParserConfigurationException,
                                                                     SAXException,
                                                                     IOException {
        return new OperatorParser().parseXml(xmlString);
    }

    /**
     * Parses an XML string into a {@code ProcessingComponent} instance using the specified container ID.
     *
     * @param containerId the ID of the container to associate the component with
     * @param xmlString the XML string to be parsed
     * @return a {@code ProcessingComponent} instance created from the parsed XML string
     * @throws ParserConfigurationException if a document builder cannot be created
     * @throws SAXException if any parsing errors occur during XML processing
     * @throws IOException if an I/O error occurs while reading the XML string
     */
    public static ProcessingComponent parse(String containerId, String xmlString) throws ParserConfigurationException,
                                                                                         SAXException, IOException {
        return new OperatorParser(containerId).parseXml(xmlString);
    }

    /**
     * Parses the given XML string into a {@code ProcessingComponent} instance.
     *
     * @param xmlString the XML string to be parsed
     * @return a {@code ProcessingComponent} instance representing the parsed data
     * @throws ParserConfigurationException if a configuration error occurs during parser setup
     * @throws SAXException if an error occurs during the parsing of the XML
     * @throws IOException if an I/O error occurs while reading the input XML string
     */
    public ProcessingComponent parseXml(String xmlString) throws ParserConfigurationException,
                                                                     SAXException,
                                                                     IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        OperatorHandler handler = this.containerId == null ? new OperatorHandler() : new OperatorHandler(containerId);
        parser.parse(new InputSource(new StringReader(xmlString)), handler);
        return handler.getResult();
    }
}
