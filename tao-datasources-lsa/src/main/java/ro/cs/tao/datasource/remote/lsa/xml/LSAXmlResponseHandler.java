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
package ro.cs.tao.datasource.remote.lsa.xml;

import org.xml.sax.Attributes;
import ro.cs.tao.datasource.remote.result.xml.XmlResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.serialization.DateAdapter;
import ro.cs.tao.utils.executors.MemoryUnit;

import java.net.URISyntaxException;
import java.util.function.Predicate;

/**
 * @author Cosmin Cara
 */
public class LSAXmlResponseHandler extends XmlResponseHandler<EOProduct> {

    private final Predicate<EOProduct> filter;

    public LSAXmlResponseHandler(String recordElementName, Predicate<EOProduct> filter) {
        super(EOProduct.class, recordElementName);
        this.filter = filter;
    }

    @Override
    protected void handleStartElement(String qName, Attributes attributes) {
        switch (qName) {
            case "link":
                final String rel = attributes.getValue(0);
                switch (rel) {
                    case "icon":
                        try {
                            this.current.setQuicklookLocation(attributes.getValue(1));
                        } catch (URISyntaxException e) {
                            logger.warning(e.getMessage());
                        }
                        break;
                    case "enclosure":
                        try {
                            this.current.setLocation(attributes.getValue(1));
                        } catch (URISyntaxException e) {
                            logger.warning(e.getMessage());
                        }
                        break;
                    default:
                        break;
                }
                break;
            case "":
                break;
            default:
                break;
        }
    }

    @Override
    protected void handleEndElement(String qName) {
        final String elementValue = buffer.toString();
        switch (qName) {
            case "title":
                if (this.current != null) {
                    this.current.setId(elementValue);
                    this.current.setName(elementValue);
                    this.current.setPixelType(PixelType.UINT16);
                    if (elementValue.startsWith("S1"))
                        this.current.setSensorType(SensorType.RADAR);
                    else if (elementValue.startsWith("S2"))
                        this.current.setSensorType(SensorType.OPTICAL);
                    else
                        this.current.setSensorType(SensorType.UNKNOWN);
                }
                break;
            case "posList":
                if (!elementValue.isEmpty()) {
                    final String[] points = elementValue.split(" ");
                    if (points.length >= 5) {
                        final Polygon2D polygon2D = new Polygon2D();
                        for (int i = 0; i < points.length; i += 2) {
                            polygon2D.append(Double.parseDouble(points[i + 1]),
                                    Double.parseDouble(points[i]));
                        }
                        this.current.setGeometry(polygon2D.toWKT(8));
                    }
                }
                this.current.addAttribute(qName, elementValue);
                break;
            case "summary":
                final String content = elementValue.substring(elementValue.indexOf("<table"), elementValue.indexOf("</table>") + 8);
                int idx = content.indexOf("<td>", content.indexOf("Orbit Number")) + 4;
                String val = content.substring(idx, content.indexOf("</td>", idx)).trim();
                this.current.addAttribute("relativeOrbit", val);
                idx = content.indexOf("<td>", content.indexOf("Orbit Direction")) + 4;
                val = content.substring(idx, content.indexOf("</td>", idx)).trim();
                this.current.addAttribute("relativeOrbit", val);
                idx = content.indexOf("<td>", content.indexOf("Platform Short Name")) + 4;
                val = content.substring(idx, content.indexOf("</td>", idx)).trim();
                this.current.setProductType(val);
                this.current.setSatelliteName(val);
                idx = content.indexOf("<td>", content.indexOf("Sensing Start Date")) + 4;
                val = content.substring(idx, content.indexOf("</td>", idx)).trim();
                try {
                    this.current.setAcquisitionDate(new DateAdapter().unmarshal(val));
                } catch (Exception e) {
                    logger.warning(e.getMessage());
                }
                // Platform Serial Identifier is returned as "B" which is not very useful (as it would be also Sentinel-2B).
                // Instead, Platform Short Name is returned as "Sentinel-2", for example, which makes more sense
                // idx = content.indexOf("<td>", content.indexOf("Platform Serial Identifier")) + 4;
                // val = content.substring(idx, content.indexOf("</td>", idx)).trim();
                // this.current.setSatelliteName(val);
                idx = content.indexOf("<td>", content.indexOf("Instrument Short Name")) + 4;
                val = content.substring(idx, content.indexOf("</td>", idx)).trim();
                this.current.addAttribute("instrument", val);
                idx = content.indexOf("<td>", content.indexOf("Cloud Coverage")) + 4;
                val = content.substring(idx, content.indexOf("</td>", idx)).trim();
                this.current.addAttribute("cloudCover", val);
                idx = content.indexOf("<td>", content.indexOf("Download Size")) + 4;
                val = content.substring(idx, content.indexOf("</td>", idx)).trim();
                long size;
                if (val.endsWith("MB")) {
                    size = (long) (MemoryUnit.MB.value() * Double.parseDouble(val.substring(0, val.indexOf(" "))));
                } else if (val.endsWith("GB")) {
                    size = (long) (MemoryUnit.GB.value() * Double.parseDouble(val.substring(0, val.indexOf(" "))));
                } else {
                    size = 0;
                }
                this.current.setApproximateSize(size);
                break;
            case "":
                break;
            default:
                break;
        }
        if (this.recordElement.equals(qName) && this.filter != null && this.filter.test(this.current)) {
            this.current = null;
        }
    }
}
