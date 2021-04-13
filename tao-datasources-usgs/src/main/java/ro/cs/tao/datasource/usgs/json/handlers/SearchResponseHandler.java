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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class SearchResponseHandler implements JSonResponseHandler<EOProduct> {
    private final ObjectMapper mapper;
    private final Pattern LANDSAT8 = Pattern.compile("LC08_.*");
    private final Pattern ECOSTRESS = Pattern.compile(".*(LSTE|ECOSTRESS|CLOUD|WUE|ESI).*");
    private final Pattern MODIS = Pattern.compile("(MOD|MCD|MYD).*");
    private final Pattern HYPERION = Pattern.compile("EO.H.*");
    private final Pattern VIIRS = Pattern.compile("VN.*");

    public SearchResponseHandler() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public List<EOProduct> readValues(String content, AttributeFilter...filters) throws IOException {
        SearchResponse response = mapper.readValue(content, SearchResponse.class);
        SearchResults responseData = response.getData();
        List<SearchResult> results = responseData.getResults();
        return results.stream().map(r -> {
            EOProduct product = null;
            String displayId = r.getDisplayId();
            if (LANDSAT8.matcher(displayId).matches()) {
                product = fillLandsat8Product(r);
            } else if (ECOSTRESS.matcher(displayId).matches()) {
                product = fillEcostressProduct(r);
            } else if (MODIS.matcher(displayId).matches()) {
                product = fillModisProduct(r);
            } else if (HYPERION.matcher(displayId).matches()) {
                product = fillHyperionProduct(r);
            } else if (VIIRS.matcher(displayId).matches()) {
                product = fillViirsProduct(r);
            }
            return product;
        }).collect(Collectors.toList());
    }

    @Override
    public long countValues(String content) throws IOException {
        SearchResponse response = mapper.readValue(content, SearchResponse.class);
        return response.getData().getTotalHits();
    }

    private EOProduct fillLandsat8Product(SearchResult result) {
        EOProduct product = new EOProduct();
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("Landsat8");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(Date.from(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant()));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(Date.from(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX")).atZone(ZoneId.systemDefault()).toInstant()));
        }
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
        }
        try {
            Browse browse = result.getBrowse().stream().filter(b -> "LandsatLook Natural Color Preview Image".equals(b.getBrowseName())).findFirst().orElse(null);
            if (browse != null) {
                product.setQuicklookLocation(browse.getBrowsePath());
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        try {
            Landsat8ProductHelper helper = new Landsat8ProductHelper(product.getName());
            product.addAttribute("tiles", helper.getPath() + helper.getRow());
        } catch (IllegalArgumentException e) {
            Logger.getLogger(SearchResponseHandler.class.getName()).fine(product.getName() + " is not a Landsat product");
        }
        return product;
    }

    private EOProduct fillEcostressProduct(SearchResult result) {
        EOProduct product = new EOProduct();
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("ECOSTRESS");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(Date.from(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant()));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(Date.from(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX")).atZone(ZoneId.systemDefault()).toInstant()));
        }
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
        }
        try {
            List<Browse> browseList = result.getBrowse();
            if (browseList.size() >= 1) {
                product.setQuicklookLocation(browseList.get(0).getBrowsePath());
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return product;
    }

    private EOProduct fillModisProduct(SearchResult result) {
        EOProduct product = new EOProduct();
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("MODIS");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(Date.from(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant()));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(Date.from(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX")).atZone(ZoneId.systemDefault()).toInstant()));
        }
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
        }
        try {
            List<Browse> browseList = result.getBrowse();
            if (browseList.size() >= 1) {
                product.setQuicklookLocation(browseList.get(0).getBrowsePath());
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return product;
    }

    private EOProduct fillViirsProduct(SearchResult result) {
        EOProduct product = new EOProduct();
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("VIIRS");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(Date.from(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant()));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(Date.from(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX")).atZone(ZoneId.systemDefault()).toInstant()));
        }
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
        }
        try {
            List<Browse> browseList = result.getBrowse();
            if (browseList.size() >= 1) {
                product.setQuicklookLocation(browseList.get(0).getBrowsePath());
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return product;
    }

    private EOProduct fillHyperionProduct(SearchResult result) {
        EOProduct product = new EOProduct();
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("HYPERION");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(Date.from(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant()));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(Date.from(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX")).atZone(ZoneId.systemDefault()).toInstant()));
        }
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
        }
        try {
            List<Browse> browseList = result.getBrowse();
            if (browseList.size() >= 1) {
                product.setQuicklookLocation(browseList.get(0).getBrowsePath());
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return product;
    }
}
