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
package ro.cs.tao.datasource.remote.peps;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Cosmin Cara
 */
public class PepsMetadataResponseHandler implements JSonResponseHandler<Boolean> {
    @Override
    public List<Boolean> readValues(String content, AttributeFilter... filters) {
        JsonReader reader = Json.createReader(new StringReader(content));
        JsonObject rootObject = reader.readObject();
        JsonObject propObject = rootObject.getJsonObject("properties");
        JsonObject storageObject = propObject.getJsonObject("storage");
        return new ArrayList<Boolean>() {{
            add(!"tape".equalsIgnoreCase(storageObject.getJsonString("mode").getString()));
        }};
    }
}
