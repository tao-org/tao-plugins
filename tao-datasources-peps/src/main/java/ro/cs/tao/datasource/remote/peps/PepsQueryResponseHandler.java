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
package ro.cs.tao.datasource.remote.peps;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.serialization.DateAdapter;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Cosmin Cara
 */
public class PepsQueryResponseHandler implements JSonResponseHandler<EOProduct> {
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public List<EOProduct> readValues(String content, AttributeFilter...filters) throws IOException {
        List<EOProduct> results = new ArrayList<>();
        JsonReader reader = Json.createReader(new StringReader(content));
        JsonObject rootObject = reader.readObject();
        JsonArray jsonArray = rootObject.getJsonArray("features");
        for (int i = 0; i < jsonArray.size(); i++) {
            try {
                JsonObject jsonObject = jsonArray.getJsonObject(i);
                EOProduct result = new EOProduct();
                result.setId(jsonObject.getString("id"));
                JsonArray coordinates = jsonObject.getJsonObject("geometry").getJsonArray("coordinates").getJsonArray(0);
                Polygon2D footprint = new Polygon2D();
                for (int j = 0; j < coordinates.size(); j++) {
                    footprint.append(coordinates.getJsonArray(j).getJsonNumber(0).doubleValue(),
                      coordinates.getJsonArray(j).getJsonNumber(1).doubleValue());
                }
                result.setGeometry(footprint.toWKT());
                JsonObject properties = jsonObject.getJsonObject("properties");
                result.setName(properties.getString("title"));
                result.setAcquisitionDate(new DateAdapter().unmarshal(properties.getString("startDate")));
                result.setFormatType(DataFormat.RASTER);
                result.setProductType(properties.getString("productType"));
                result.setPixelType(PixelType.UINT16);
                result.setLocation(properties.getJsonObject("services").getJsonObject("download").getString("url"));
                result.setApproximateSize(properties.getJsonObject("services").getJsonObject("download").getJsonNumber("size").longValue());
                Collection collection = Enum.valueOf(Collection.class, properties.getString("collection"));
                switch (collection) {
                    case S1:
                        result.setSensorType(SensorType.RADAR);
                        break;
                    case S2:
                    case S2ST:
                        result.setSensorType(SensorType.OPTICAL);
                        break;
                    case S3:
                        result.setSensorType(SensorType.ATMOSPHERIC);
                        break;
                }
                properties.forEach((key, value) -> result.addAttribute(key, value.toString()));
                results.add(result);
            } catch (Exception ex) {
                throw new IOException(ex.getMessage());
            }
        }
        return results;
    }
}
