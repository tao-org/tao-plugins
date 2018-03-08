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

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Cosmin Cara
 */
public class ResponseHandler implements JSonResponseHandler<EOProduct> {
    @Override
    public List<EOProduct> readValues(String content, AttributeFilter... filters) throws IOException {
        List<EOProduct> results = new ArrayList<>();
        final JsonReader jsonReader = Json.createReader(new StringReader(content));
        JsonObject responseObj = jsonReader.readObject();
        JsonArray jsonArray = responseObj.getJsonArray("results");
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject result = jsonArray.getJsonObject(i);
            // ignore products that are in the T2 or RT category
            if (!"T1".equals(result.getString("COLLECTION_CATEGORY", ""))) {
                continue;
            }
            EOProduct currentProduct = new EOProduct();
            currentProduct.setFormatType(DataFormat.RASTER);
            currentProduct.setSensorType(SensorType.OPTICAL);
            currentProduct.setPixelType(PixelType.UINT16);
            currentProduct.setName(result.getString("product_id"));
            currentProduct.setId(result.getString("scene_id"));
            currentProduct.setProductType(result.getString("satellite_name"));

            try {
                currentProduct.setAcquisitionDate(new SimpleDateFormat("yyyy-MM-dd").parse(result.getString("acquisitionDate")));
            } catch (Exception e) {
                e.printStackTrace();
            }
            Polygon2D footprint = new Polygon2D();
            footprint.append(result.getJsonNumber("upperLeftCornerLongitude").doubleValue(),
                             result.getJsonNumber("upperLeftCornerLatitude").doubleValue());
            footprint.append(result.getJsonNumber("upperRightCornerLongitude").doubleValue(),
                             result.getJsonNumber("upperRightCornerLatitude").doubleValue());
            footprint.append(result.getJsonNumber("lowerRightCornerLongitude").doubleValue(),
                             result.getJsonNumber("lowerRightCornerLatitude").doubleValue());
            footprint.append(result.getJsonNumber("lowerLeftCornerLongitude").doubleValue(),
                             result.getJsonNumber("lowerLeftCornerLatitude").doubleValue());
            footprint.append(result.getJsonNumber("upperLeftCornerLongitude").doubleValue(),
                             result.getJsonNumber("upperLeftCornerLatitude").doubleValue());
            currentProduct.setGeometry(footprint.toWKT());
            try {
                currentProduct.setLocation(result.getJsonObject("download_links").getString("usgs"));
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            results.add(currentProduct);
        }
        return results;
    }
}
