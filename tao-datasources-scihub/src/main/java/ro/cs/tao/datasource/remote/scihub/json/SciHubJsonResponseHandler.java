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
package ro.cs.tao.datasource.remote.scihub.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.datasource.remote.scihub.download.SentinelDownloadStrategy;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.serialization.DateAdapter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class SciHubJsonResponseHandler implements JSonResponseHandler<EOProduct> {
    @Override
    public List<EOProduct> readValues(String content, AttributeFilter...filters) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Result[] results = mapper.readValue(content, Result[].class);
        SentinelDownloadStrategy downloader = new SentinelDownloadStrategy("");
        return Arrays.stream(results).map(r -> {
            try {
                EOProduct product = new EOProduct();
                product.setName(r.getIdentifier());
                product.setId(r.getUuid());
                product.setFormatType(DataFormat.RASTER);
                product.setSensorType(SensorType.OPTICAL);
                product.setPixelType(PixelType.UINT16);
                product.setWidth(-1);
                product.setHeight(-1);
                Polygon2D footprint = new Polygon2D();
                for (List<Double> doubleList : r.getFootprint().get(0)) {
                    footprint.append(doubleList.get(0), doubleList.get(1));
                }
                product.setGeometry(footprint.toWKT());
                product.setProductType(r.getProductType());
                product.setLocation(downloader.getProductUrl(product));
                r.getIndexes().forEach(i -> i.getChildren().forEach(c -> {
                    String cName = c.getName();
                    if ("Sensing start".equals(cName)) {
                        try {
                            product.setAcquisitionDate(new DateAdapter().unmarshal(c.getValue()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (!"Footprint".equals(cName)) {
                        product.addAttribute(cName, c.getValue());
                    }
                }));
                return product;
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }).collect(Collectors.toList());
    }
}
