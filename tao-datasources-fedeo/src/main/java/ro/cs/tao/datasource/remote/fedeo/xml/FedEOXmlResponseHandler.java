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
package ro.cs.tao.datasource.remote.fedeo.xml;

import org.xml.sax.Attributes;
import ro.cs.tao.datasource.remote.result.xml.XmlResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.serialization.DateAdapter;

import java.net.URISyntaxException;

/**
 * @author Cosmin Cara
 */
public class FedEOXmlResponseHandler extends XmlResponseHandler<EOProduct> {

    private String identifiedElement;

    public FedEOXmlResponseHandler(String recordElementName) {
        super(EOProduct.class, recordElementName);
    }

    @Override
    protected void handleStartElement(String qName, Attributes attributes) {
        switch (qName) {
            case "platform":
                this.identifiedElement = qName;
                break;
            case "instrument":
                this.identifiedElement = qName;
                break;
            case "browse":
                this.identifiedElement = qName;
                break;
            case "ServiceReference":
                if (this.identifiedElement.equals("QUICKLOOK")) {
                    try {
                        this.current.setQuicklookLocation(attributes.getValue(0));
                    } catch (URISyntaxException e) {
                        logger.warning(e.getMessage());
                    }
                }
                if (this.identifiedElement.equals("product") && this.current.getLocation() == null) {
                    try {
                        this.current.setLocation(attributes.getValue(0));
                    } catch (URISyntaxException e) {
                        logger.warning(e.getMessage());
                    }
                }
                break;
            case "product":
                this.identifiedElement = qName;
                break;
            case "link":
                if(this.current != null && attributes.getValue("rel") != null && attributes.getValue("rel").equals("enclosure")) {
                    try {
                        this.current.setLocation(attributes.getValue("href"));
                    } catch (URISyntaxException e) {
                        logger.warning(e.getMessage());
                    }
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
        final String elementValue = buffer.toString().replaceAll(".*?:","");
        switch (qName) {
            case "identifier":
                if (this.current != null) {
                    this.current.setId(elementValue);
                    this.current.setName(elementValue);
                    this.current.setPixelType(PixelType.UINT16);
                    this.current.setSensorType(SensorType.UNKNOWN);
                }
                break;
            case "date":
                if (elementValue.isEmpty()) {
                    break;
                }
                String[] dates = elementValue.split("/");
                if (dates.length < 2) {
                    break;
                }
                try {
                    this.current.setAcquisitionDate(new DateAdapter().unmarshal(dates[0]));
                    this.current.addAttribute("beginPosition", dates[0]);
                    this.current.addAttribute("endPosition", dates[1]);
                } catch (Exception e) {
                    logger.warning(e.getMessage());
                }
                break;
            case "beginPosition":
                if (elementValue.isEmpty()) {
                    break;
                }
                try {
                    this.current.setAcquisitionDate(new DateAdapter().unmarshal(elementValue));
                } catch (Exception e) {
                    logger.warning(e.getMessage());
                }
                this.current.addAttribute(qName, elementValue);
                break;
            case "endPosition":
                this.current.addAttribute(qName, elementValue);
                break;
            case "shortName":
                switch (this.identifiedElement) {
                    case "platform":
                        this.current.setProductType(elementValue);
                        this.current.addAttribute(this.identifiedElement, elementValue);
                        break;
                    case "instrument":
                        this.current.addAttribute(this.identifiedElement, elementValue);
                        break;
                    default:
                        break;
                }
                break;
            case "serialIdentifier":
                this.current.setSatelliteName(elementValue);
                break;
            case "sensorType":
                try {
                    this.current.setSensorType(SensorType.valueOf(elementValue));
                } catch (IllegalArgumentException ex) {
                    this.current.setSensorType(SensorType.UNKNOWN);
                }
                this.current.addAttribute(qName, elementValue);
                break;
            case "operationalMode":
                this.current.addAttribute(qName, elementValue);
                break;
            case "orbitNumber":
                this.current.addAttribute(qName, elementValue);
                break;
            case "wrsLongitudeGrid":
                this.current.addAttribute(qName, elementValue);
                break;
            case "wrsLatitudeGrid":
                this.current.addAttribute(qName, elementValue);
                break;
            case "illuminationAzimuthAngle":
                this.current.addAttribute(qName, elementValue);
                break;
            case "illuminationElevationAngle":
                this.current.addAttribute(qName, elementValue);
                break;
            case "incidenceAngle":
                this.current.addAttribute(qName, elementValue);
                break;
            case "posList":
                if (!elementValue.isEmpty()) {
                    String[] points = elementValue.split(" ");
                    if (points.length >= 5) {
                        Polygon2D polygon2D = new Polygon2D();
                        for (int i = 0; i < points.length; i += 2) {
                            polygon2D.append(Double.parseDouble(points[i + 1].replaceAll("[^e\\d\\-\\.]","")),
                                    Double.parseDouble(points[i].replaceAll("[^e\\d\\-\\.]","")));
                        }
                        this.current.setGeometry(polygon2D.toWKT(8));
                    }
                }
                this.current.addAttribute(qName, elementValue);
                break;
            case "type":
                if (this.identifiedElement.equals("browse")) {
                    this.identifiedElement = elementValue;
                }
                break;
            case "size":
                this.current.setApproximateSize(Long.parseLong(elementValue));
                break;
            case "cloudCoverPercentage":
                this.current.addAttribute(qName, elementValue);
                break;
            case "acquisitionType":
                this.current.addAttribute(qName, elementValue);
                break;
            case "productType":
                this.current.addAttribute(qName, elementValue);
                break;
            case "status":
                this.current.addAttribute(qName, elementValue);
                break;
            case "":
                break;
            default:
                break;
        }
    }
}
