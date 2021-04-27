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

/**
 * @author Cosmin Cara
 */
public class FedEOCollectionXmlResponseHandler extends XmlResponseHandler<String> {

    private static final String LINK = "link";
    private static final String TARGET_ATTRIBUTE_REL_VALUE = "search";

    FedEOCollectionXmlResponseHandler(String recordElementName) {
        super(String.class, recordElementName);
    }

    @Override
    protected void handleStartElement(String qName, Attributes attributes) {
        if (this.current != null && qName.contentEquals(LINK) && attributes.getValue(2) != null && attributes.getValue(2).contentEquals(TARGET_ATTRIBUTE_REL_VALUE)) {
            String searchURL = attributes.getValue(0);
            String collectionName = searchURL.replaceAll("[\\s\\S]*parentIdentifier=(.*?)((&[\\s\\S]*)|$)", "$1");
            if (!collectionName.isEmpty()) {
                this.current = collectionName;
            }
        }
    }

    @Override
    protected void handleEndElement(String qName) {
        if (this.recordElement.contentEquals(qName) && this.current != null && this.current.isEmpty()) {
            this.current = null;
        }
    }
}
