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

package ro.cs.tao.opensearch;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import ro.cs.tao.datasource.param.DataSourceParameter;
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.opensearch.model.OpenSearchDescription;
import ro.cs.tao.opensearch.model.Url;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OpenSearchDescriptionParser {

    public OpenSearchDescription parse(InputStream content) throws Exception {
        Handler handler = new Handler();
        OpenSearchDescription result;
        InputSource inputSource = new InputSource(content);
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        parser.parse(inputSource, handler);
        result = handler.getResult();
        return result;
    }

    private class Handler extends DefaultHandler {
        private OpenSearchDescription description;
        private Url currentUrl;
        private ParameterName currentParameter;
        private Class currentParamClass;
        private List<String> currentOptions;
        private StringBuilder buffer;

        public OpenSearchDescription getResult() { return description; }

        @Override
        public void startDocument() throws SAXException {
            this.description = new OpenSearchDescription();
            this.buffer = new StringBuilder();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (qName.indexOf(":") > 0) {
                qName = qName.substring(qName.indexOf(":") + 1);
            }
            buffer.setLength(0);
            switch (qName) {
                case "Url":
                    this.currentUrl = new Url();
                    this.currentUrl.setType(attributes.getValue("type"));
                    String url = attributes.getValue("template");
                    if (url.indexOf("?") > 0) {
                        url = url.substring(0, url.indexOf("?"));
                    }
                    this.currentUrl.setTemplate(url);
                    this.currentUrl.setRel(attributes.getValue("rel"));
                    break;
                case "Parameter":
                    String attributeValue = attributes.getValue("name");
                    this.currentParameter = ParameterName.create(attributeValue, attributeValue, attributes.getValue("title"));
                    this.currentParamClass = attributes.getValue("pattern") != null && attributes.getValue("pattern").equals("[0-9]+") ?
                            Integer.class :
                            attributes.getValue("minInclusive") != null || attributes.getValue("minExclusive") != null ?
                                    Float.class :
                                    attributes.getValue("value") != null &&
                                    (attributes.getValue("value").startsWith("{time:") ||
                                     attributes.getValue("value").startsWith("{date:")) ?
                                            LocalDateTime.class : String.class;
                    break;
                case "Option":
                    if (this.currentOptions == null) {
                        this.currentOptions = new ArrayList<>();
                    }
                    this.currentOptions.add(attributes.getValue("value"));
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (qName.indexOf(":") > 0) {
                qName = qName.substring(qName.indexOf(":") + 1);
            }
            switch (qName) {
                case "ShortName":
                    this.description.setShortName(buffer.toString());
                    break;
                case "Description":
                    this.description.setDescription(buffer.toString());
                    break;
                case "Tags":
                    this.description.setTags(buffer.toString());
                    break;
                case "Url":
                    if (this.currentUrl != null) {
                        this.description.addUrl(this.currentUrl);
                    }
                    break;
                case "Parameter":
                    if (this.currentOptions != null) {
                        this.currentUrl.addParameter(new DataSourceParameter(this.currentParameter.getSystemName(),
                                                                             this.currentParameter.getRemoteName(),
                                                                             String.class,
                                                                             this.currentParameter.getLabel(),
                                                                             null, false,
                                                                             this.currentOptions.toArray(new String[0])));
                        this.currentOptions = null;
                    } else {
                        this.currentUrl.addParameter(new DataSourceParameter(this.currentParameter.getSystemName(),
                                                                             this.currentParameter.getRemoteName(),
                                                                             this.currentParamClass,
                                                                             this.currentParameter.getLabel(),
                                                                             false));
                        this.currentParamClass = null;
                    }
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            buffer.append(new String(ch, start, length).replace("\n", ""));
        }
    }

}
