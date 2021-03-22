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
import ro.cs.tao.serialization.DateAdapter;

import java.net.URISyntaxException;

/**
 * @author Cosmin Cara
 */
public class FedEOCollectionXmlResponseHandler extends XmlResponseHandler<String> {

    private static final String IDENTIFIER = "identifier";

    public FedEOCollectionXmlResponseHandler(String recordElementName) {
        super(String.class, recordElementName);
    }

    @Override
    protected void handleStartElement(String qName, Attributes attributes) {

    }

    @Override
    protected void handleEndElement(String qName) {
        final String elementValue = buffer.toString();
        switch (qName) {
            case IDENTIFIER:
                this.current = elementValue;
                break;
            default:
                break;
        }
    }
}
