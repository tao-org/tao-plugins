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

public class OperatorParser {
    private final String containerId;

    public OperatorParser() {
        this.containerId = null;
    }

    public OperatorParser(String containerId) {
        this.containerId = containerId;
    }

    public static ProcessingComponent parse(String xmlString) throws ParserConfigurationException,
                                                                     SAXException,
                                                                     IOException {
        return new OperatorParser().parseXml(xmlString);
    }

    public static ProcessingComponent parse(String containerId, String xmlString) throws ParserConfigurationException,
                                                                                         SAXException, IOException {
        return new OperatorParser(containerId).parseXml(xmlString);
    }

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
