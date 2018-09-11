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
/**
 * @author Cosmin Cara
 */
public class SciHubXmlCountResponseHandler extends XmlResponseHandler<Long> {

    public SciHubXmlCountResponseHandler(String recordElementName) {
        super(Long.class, recordElementName);
    }

    @Override
    protected void handleStartElement(String qName, Attributes attributes) {
    }

    @Override
    protected void handleEndElement(String qName) {
        if (this.recordElement.equals(qName)) {
            final String elementValue = buffer.toString();
            try {
                this.current = Long.parseLong(elementValue);
            } catch (NumberFormatException nfe) {
                this.current = 0L;
            }
        }
    }
}
