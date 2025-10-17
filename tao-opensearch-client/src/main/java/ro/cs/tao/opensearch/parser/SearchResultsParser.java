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
import ro.cs.tao.datasource.param.ParameterName;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.Visibility;
import ro.cs.tao.opensearch.model.Content;
import ro.cs.tao.opensearch.model.Entry;
import ro.cs.tao.opensearch.model.Group;
import ro.cs.tao.opensearch.model.Link;
import ro.cs.tao.utils.DateUtils;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchResultsParser {

    public List<EOProduct> parse(InputStream content) throws Exception {
        Handler handler = new Handler();
        List<EOProduct> result;
        InputSource inputSource = new InputSource(content);
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        parser.parse(inputSource, handler);
        result = handler.getResult();
        return result;
    }

    private class Handler extends DefaultHandler {
        private static final Pattern missionPattern = Pattern.compile("Mission&lt;\\/b&gt;: ([a-zA-Z0-9_.-]+) &lt;");
        private List<EOProduct> results;
        private Entry currentEntry;
        private Group currentGroup;
        private ParameterName currentParameter;
        private StringBuilder buffer;

        public List<EOProduct> getResult() { return results; }

        @Override
        public void startDocument() throws SAXException {
            this.results = new ArrayList<>();
            this.buffer = new StringBuilder();
            this.currentEntry = null;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (qName.indexOf(":") > 0) {
                qName = qName.substring(qName.indexOf(":") + 1);
            }
            buffer.setLength(0);
            switch (qName) {
                case "entry":
                    this.currentEntry = new Entry();
                    break;
                case "link":
                    if (this.currentEntry != null) {
                        Link link = new Link();
                        link.setTitle(attributes.getValue("title"));
                        link.setRel(attributes.getValue("rel"));
                        link.setHref(attributes.getValue("href"));
                        this.currentEntry.addLink(link);
                    }
                    break;
                case "group":
                    this.currentGroup = new Group();
                    break;
                case "content":
                    if (this.currentGroup != null) {
                        this.currentGroup.setContent(new Content());
                        this.currentGroup.getContent().setText(attributes.getValue("type"));
                        this.currentGroup.getContent().setUrl(attributes.getValue("url"));
                        this.currentEntry.setGroup(this.currentGroup);
                        this.currentGroup = null;
                    }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (qName.indexOf(":") > 0) {
                qName = qName.substring(qName.indexOf(":") + 1);
            }
            switch (qName) {
                case "content":
                    if (this.currentEntry != null) {
                        this.currentEntry.setContent(new Content());
                        this.currentEntry.getContent().setText(buffer.toString());
                    }
                    break;
                case "date":
                    if (this.currentEntry != null) {
                        this.currentEntry.setDate(buffer.toString());
                    }
                    break;
                case "group":
                    if (this.currentEntry != null && this.currentGroup != null) {
                        this.currentEntry.setGroup(this.currentGroup);
                    }
                    break;
                case "title":
                    if (this.currentEntry != null) {
                        this.currentEntry.setTitle(buffer.toString());
                    }
                    break;
                case "id":
                    if (this.currentEntry != null) {
                        this.currentEntry.setId(buffer.toString());
                    }
                    break;
                case "identifier":
                    if (this.currentEntry != null) {
                        this.currentEntry.setIdentifier(buffer.toString());
                    }
                    break;
                case "entry":
                    this.results.add(toProduct(this.currentEntry));
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            buffer.append(new String(ch, start, length).replace("\n", ""));
        }

        private EOProduct toProduct(Entry entry) {
            final EOProduct product = new EOProduct();
            product.setId(entry.getId());
            product.setName(entry.getTitle());
            String date = entry.getDate();
            LocalDateTime acquisitionDate = date != null ? DateUtils.parseDateTime(date.split("/")[0]) : null;
            product.setAcquisitionDate(acquisitionDate);
            product.setFormatType(DataFormat.RASTER);
            product.setVisibility(Visibility.PUBLIC);
            try {
                product.setLocation(entry.getLink().stream().filter(l -> l.getRel().equals("enclosure")).map(Link::getHref).findFirst().orElse(null));
                product.setQuicklookLocation(entry.getGroup().getContent().getUrl());
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
            String text = entry.getContent().getText();
            Matcher matcher = missionPattern.matcher(text);
            if (matcher.find()) {
                product.setSatelliteName(matcher.group(1));
            }
            return product;
        }
    }

}
