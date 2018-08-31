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
package ro.cs.tao.datasource.remote.usgs.json;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;

import javax.json.*;
import java.io.IOException;
import java.io.StringReader;
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
        int found = 0;
        if (jsonObj != null) {
            JsonNumber jsonNumber = jsonObj.getJsonNumber("found");
            if (jsonNumber != null) {
                found = jsonNumber.intValue();
            }
        }
        if (found > 0) {
            JsonArray jsonArray = responseObj.getJsonArray("results");
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject result = jsonArray.getJsonObject(i);
                // ignore products that are in the T2 or RT category
                if (!"T1".equals(result.getString("COLLECTION_CATEGORY", ""))) {
                    found--;
                }
            }
        }
        results.add(found);
        return results;
    }
}
