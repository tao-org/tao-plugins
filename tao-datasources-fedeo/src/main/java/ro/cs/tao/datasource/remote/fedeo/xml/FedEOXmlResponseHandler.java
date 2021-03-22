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
import ro.cs.tao.eodata.enums.SensorType;

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
                if (this.identifiedElement.equals("product")) {
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
                }
                break;
            case "beginPosition":
                this.current.addAttribute(qName, elementValue);
                break;
            case "endPosition":
                this.current.addAttribute(qName, elementValue);
                break;
            case "shortName":
                switch (this.identifiedElement) {
                    case "platform":
                        this.current.addAttribute(this.identifiedElement, elementValue);
                        break;
                    case "instrument":
                        this.current.addAttribute(this.identifiedElement, elementValue);
                        break;
                    default:
                        break;
                }
                break;
            case "sensorType":
                this.current.setSensorType(SensorType.valueOf(elementValue));
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
            case "posList":
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
