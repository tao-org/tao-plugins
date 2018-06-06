/*
 * Copyright (C) 2017 CS ROMANIA
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
package ro.cs.tao.datasource.remote.usgs.json;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;

import javax.json.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Cosmin Cara
 */
public class CountResponseHandler implements JSonResponseHandler<Integer> {
    @Override
    public List<Integer> readValues(String content, AttributeFilter... filters) throws IOException {
        List<Integer> results = new ArrayList<>();
        final JsonReader jsonReader = Json.createReader(new StringReader(content));
        JsonObject responseObj = jsonReader.readObject();
        JsonObject jsonObj = responseObj.getJsonObject("meta");
        if (jsonObj != null) {
            JsonNumber jsonNumber = jsonObj.getJsonNumber("found");
            if (jsonNumber != null) {
                results.add(jsonNumber.intValue());
            }
        }
        return results;
    }
}
