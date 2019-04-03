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
package ro.cs.tao.datasource.usgs.json.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.datasource.usgs.json.responses.SearchResponse;
import ro.cs.tao.datasource.usgs.json.responses.SearchResult;
import ro.cs.tao.datasource.usgs.json.responses.SearchResults;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Date;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class SearchResponseHandler implements JSonResponseHandler<EOProduct> {
    @Override
    public List<EOProduct> readValues(String content, AttributeFilter...filters) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        SearchResponse response = mapper.readValue(content, SearchResponse.class);
        SearchResults responseData = response.getData();
        List<SearchResult> results = responseData.getResults();
        return results.stream().map(r -> {
            EOProduct product = new EOProduct();
            product.setFormatType(DataFormat.RASTER);
            product.setSensorType(SensorType.OPTICAL);
            product.setPixelType(PixelType.UINT16);
            product.setProductType("Landsat8");
            product.setId(r.getEntityId());
            product.setName(r.getDisplayId());
            product.setAcquisitionDate(Date.from(r.getAcquisitionDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = r.getSpatialFootprint().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
            try {
                product.setQuicklookLocation(r.getBrowseUrl());
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            try {
                product.setLocation(r.getDownloadUrl());
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            return product;
        }).collect(Collectors.toList());

    }

    @Override
    public long countValues(String content) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        SearchResponse response = mapper.readValue(content, SearchResponse.class);
        return response.getData().getTotalHits();
    }
}
