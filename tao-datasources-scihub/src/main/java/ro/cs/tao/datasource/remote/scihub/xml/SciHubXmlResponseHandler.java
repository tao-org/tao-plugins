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
package ro.cs.tao.datasource.remote.scihub.xml;

import org.xml.sax.Attributes;
import ro.cs.tao.datasource.remote.result.xml.XmlResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.serialization.DateAdapter;
import ro.cs.tao.utils.executors.MemoryUnit;

import java.net.URISyntaxException;

/**
 * @author Cosmin Cara
 */
public class SciHubXmlResponseHandler extends XmlResponseHandler<EOProduct> {

    private String identifiedElement;
    private String secondaryAttributeValue;

    public SciHubXmlResponseHandler(String recordElementName) {
        super(EOProduct.class, recordElementName);
    }

    @Override
    protected void handleStartElement(String qName, Attributes attributes) {
        if (this.recordElement.equals(qName)) {
            this.current.setSensorType(SensorType.OPTICAL);
            this.current.setPixelType(PixelType.UINT16);
            this.current.setWidth(-1);
            this.current.setHeight(-1);
        } else {
            final String attributeValue = attributes.getValue(0);
            switch (qName) {
                case "str":
                case "double":
                case "date":
                case "int":
                case "link":
                    this.identifiedElement = attributeValue;
                    if (attributes.getLength() > 1) {
                        this.secondaryAttributeValue = attributes.getValue(1);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void handleEndElement(String qName) {
        final String elementValue = buffer.toString();
        switch (qName) {
            case "id":
                if (this.current != null) {
                    this.current.setId(elementValue);
                }
                break;
            case "str":
                try {
                    switch (this.identifiedElement) {
                        case "identifier":
                            this.current.setName(elementValue);
                            if (elementValue.startsWith("S1"))
                                this.current.setSensorType(SensorType.RADAR);
                            else if (elementValue.startsWith("S2"))
                                this.current.setSensorType(SensorType.OPTICAL);
                            else if (elementValue.startsWith("S3"))
                                this.current.setSensorType(SensorType.ATMOSPHERIC);
                            break;
                        case "footprint":
                            this.current.setGeometry(elementValue);
                            break;
                        case "platformname":
                            this.current.setProductType(elementValue);
                            break;
                        case "platformserialidentifier":
                            this.current.setSatelliteName(elementValue);
                            break;
                        case "size":
                            long size;
                            if (elementValue.endsWith("MB")) {
                                size = (long) (MemoryUnit.MB.value() * Double.parseDouble(elementValue.substring(0, elementValue.indexOf(" "))));
                            } else if (elementValue.endsWith("GB")) {
                                size = (long) (MemoryUnit.GB.value() * Double.parseDouble(elementValue.substring(0, elementValue.indexOf(" "))));
                            } else {
                                size = 0;
                            }
                            this.current.setApproximateSize(size);
                            break;
                        default:
                            this.current.addAttribute(this.identifiedElement, elementValue);
                            break;
                    }
                } catch (Exception e) {
                    logger.warning(e.getMessage());
                }
                break;
            case "int":
                this.current.addAttribute(this.identifiedElement, elementValue);
                break;
            case "double":
                try {
                    switch (this.identifiedElement) {
                        case "cloudcoverpercentage":
                            this.current.addAttribute(this.identifiedElement, elementValue);
                            break;
                        default:
                            this.current.addAttribute(this.identifiedElement, elementValue);
                            break;
                    }
                } catch (Exception e) {
                    logger.warning(e.getMessage());
                }
                break;
            case "date":
                try {
                    switch (this.identifiedElement) {
                        case "beginposition":
                            int dotIdx = elementValue.lastIndexOf(".");
                            this.current.setAcquisitionDate(new DateAdapter().unmarshal(dotIdx > 0 ?
                                    elementValue.substring(0, dotIdx) : elementValue));
                            break;
                        default:
                            this.current.addAttribute(this.identifiedElement, elementValue);
                            break;
                    }
                } catch (Exception e) {
                    logger.warning(e.getMessage());
                }
                break;
            case "link":
                if (this.current != null) {
                    try {
                        if (this.current.getLocation() == null) {
                                this.current.setLocation(this.identifiedElement);
                        } else if ("icon".equals(this.identifiedElement)) {
                            this.current.setQuicklookLocation(this.secondaryAttributeValue);
                            this.secondaryAttributeValue = null;
                        }
                    } catch (URISyntaxException e) {
                        logger.warning(e.getMessage());
                    }
                }
                break;
            default:
                break;
        }
    }
}
