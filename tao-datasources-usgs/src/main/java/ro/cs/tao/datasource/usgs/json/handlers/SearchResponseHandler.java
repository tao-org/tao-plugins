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

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.datasource.usgs.json.responses.SearchResponse;
import ro.cs.tao.datasource.usgs.json.responses.SearchResult;
import ro.cs.tao.datasource.usgs.json.responses.SearchResults;
import ro.cs.tao.datasource.usgs.json.types.Browse;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.products.landsat.Landsat8ProductHelper;
import ro.cs.tao.serialization.JsonMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class SearchResponseHandler implements JSonResponseHandler<EOProduct> {
    private static final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Set<String> quickLookElements = new HashSet<>() {{
        add("LandsatLook Natural Color Preview Image");
        add("Thermal Browse");
        add("Reflective Browse");
        add("Quality Browse");
    }};
    private final String sensorName;
    private final SensorType sensorType;
    private final Predicate<EOProduct> filter;

    public SearchResponseHandler(String sensorName, SensorType type, Predicate<EOProduct> filter) {
        this.sensorName = sensorName;
        this.sensorType = type;
        this.filter = filter;
    }

    @Override
    public List<EOProduct> readValues(String content, AttributeFilter...filters) throws IOException {
        SearchResponse response = JsonMapper.instance().readValue(content, SearchResponse.class);
        SearchResults responseData = response.getData();
        if (responseData == null) {
            throw new IllegalStateException("Fail to get the search results. Reason: " + response.getErrorMessage());
        }
        List<SearchResult> results = responseData.getResults();
        return results != null ? results.stream().map(r -> {
            final EOProduct product = fillProduct(r);
            if (product == null) {
                Logger.getLogger(SearchResponseHandler.class.getName()).finest(r.getDisplayId() + " is not a supported product");
            }
            return product;
        }).filter(Objects::nonNull).collect(Collectors.toList()) : new ArrayList<>();
    }

    @Override
    public long countValues(String content) throws IOException {
        final SearchResponse response = JsonMapper.instance().readValue(content, SearchResponse.class);
        final SearchResults responseData = response.getData();
        if (responseData == null) {
            throw new IllegalStateException("Fail to get the count of the search results. Reason: " + response.getErrorMessage());
        }
        return responseData.getTotalHits();
    }

    private EOProduct fillProduct(SearchResult result) {
        final EOProduct product = new EOProduct();
        if (result.getSpatialCoverage() != null) {
            final Polygon2D polygon2D = new Polygon2D();
            final Number[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            if (coordinates != null && coordinates.length > 0) {
                for (Number[] point : coordinates[0]) {
                    polygon2D.append(point[0].doubleValue(), point[1].doubleValue());
                }
                product.setGeometry(polygon2D.toWKT());
            }
            if (this.filter != null && this.filter.test(product)) {
                return null;
            }
        }
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(this.sensorType);
        product.setPixelType(PixelType.UINT16);
        product.setProductType(this.sensorName);
        product.setSatelliteName(this.sensorName);
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        final String temporalCoverage = result.getTemporalCoverage().getStartDate().replaceAll("(.+?)\\s+.+", "$1");
        product.setAcquisitionDate(LocalDate.parse(temporalCoverage, format).atStartOfDay());
        try {
            final Browse browse = result.getBrowse().stream().filter(b -> quickLookElements.contains(b.getBrowseName())).findFirst().orElse(null);
            if (browse != null) {
                product.setQuicklookLocation(browse.getBrowsePath());
            }
        } catch (URISyntaxException e) {
            Logger.getLogger(SearchResponseHandler.class.getName()).severe(e.getMessage());
        }
        try {
            if (this.sensorName.toLowerCase().startsWith("landsat")) {
                final Landsat8ProductHelper helper = new Landsat8ProductHelper(product.getName());
                product.addAttribute("tiles", helper.getPath() + helper.getRow());
            }
        } catch (IllegalArgumentException e) {
            Logger.getLogger(SearchResponseHandler.class.getName()).finest(product.getName() + " is not a Landsat product");
        }
        product.addAttribute("downloadable", result.getOptions() != null ? String.valueOf(result.getOptions().isDownload()) : "true");
        return product;
    }

}
