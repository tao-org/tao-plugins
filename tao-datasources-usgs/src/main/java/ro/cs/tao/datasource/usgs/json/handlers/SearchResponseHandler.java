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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
public class SearchResponseHandler implements JSonResponseHandler<EOProduct> {
    private static final DateTimeFormatter firstFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter secondFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX");
    private static final Set<String> quickLookElements = new HashSet<String>() {{
        add("LandsatLook Natural Color Preview Image");
        add("Thermal Browse");
    }};

    private final Pattern LANDSAT8 = Pattern.compile("L[CTO]08_.*");
    private final Pattern LANDSAT9 = Pattern.compile("L[CTO]09_.*");
    private final Pattern ECOSTRESS = Pattern.compile(".*(BGEO|L1BRAD|L1BMAPRAD|ESIPTJPL|L3ETALEXIU|ETPTJPL|ANCQA|L4ESIALEXIU|LSTE|ECOSTRESS|CLOUD|WUE|ESI).*");
    private final Pattern MODIS = Pattern.compile("(MOD|MCD|MYD).*");
    private final Pattern HYPERION = Pattern.compile("EO.H.*");
    private final Pattern VIIRS = Pattern.compile("VN.*");

    private final Predicate<EOProduct> filter;

    public SearchResponseHandler(Predicate<EOProduct> filter) {
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
            EOProduct product = null;
            String displayId = r.getDisplayId();
            if (LANDSAT8.matcher(displayId).matches()) {
                product = fillLandsat8Product(r);
            } else if (LANDSAT9.matcher(displayId).matches()) {
                product = fillLandsat9Product(r);
            } else if (ECOSTRESS.matcher(displayId).matches()) {
                product = fillEcostressProduct(r);
            } else if (MODIS.matcher(displayId).matches()) {
                product = fillModisProduct(r);
            } else if (HYPERION.matcher(displayId).matches()) {
                product = fillHyperionProduct(r);
            } else if (VIIRS.matcher(displayId).matches()) {
                product = fillViirsProduct(r);
            }
            /*if (product == null) {
                Logger.getLogger(SearchResponseHandler.class.getName()).finest(displayId + " is not a supported product");
            }*/
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

    private EOProduct fillLandsat8Product(SearchResult result) {
        EOProduct product = new EOProduct();
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
            if (this.filter != null && this.filter.test(product)) {
                return null;
            }
        }
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("Landsat8");
        product.setSatelliteName("Landsat8");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), firstFormat));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), secondFormat));
        }
        try {
            Browse browse = result.getBrowse().stream().filter(b -> quickLookElements.contains(b.getBrowseName())).findFirst().orElse(null);
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
            Logger.getLogger(SearchResponseHandler.class.getName()).finest(product.getName() + " is not a Landsat product");
        }
        product.addAttribute("downloadable", result.getOptions() != null ? String.valueOf(result.getOptions().isDownload()) : "true");
        return product;
    }

    private EOProduct fillLandsat9Product(SearchResult result) {
        EOProduct product = new EOProduct();
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
            if (this.filter != null && this.filter.test(product)) {
                return null;
            }
        }
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("Landsat9");
        product.setSatelliteName("Landsat9");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), firstFormat));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), secondFormat));
        }
        try {
            Browse browse = result.getBrowse().stream().filter(b -> quickLookElements.contains(b.getBrowseName())).findFirst().orElse(null);
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
            Logger.getLogger(SearchResponseHandler.class.getName()).finest(product.getName() + " is not a Landsat product");
        }
        product.addAttribute("downloadable", result.getOptions() != null ? String.valueOf(result.getOptions().isDownload()) : "true");
        return product;
    }

    private EOProduct fillEcostressProduct(SearchResult result) {
        EOProduct product = new EOProduct();
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
            if (this.filter != null && this.filter.test(product)) {
                return null;
            }
        }
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("ECOSTRESS");
        product.setSatelliteName("ECOSTRESS");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), firstFormat));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), secondFormat));
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
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
            if (this.filter != null && this.filter.test(product)) {
                return null;
            }
        }
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("MODIS");
        product.setSatelliteName("MODIS");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), firstFormat));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), secondFormat));
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
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
            if (this.filter != null && this.filter.test(product)) {
                return null;
            }
        }
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("VIIRS");
        product.setSatelliteName("VIIRS");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), firstFormat));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), secondFormat));
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
        if (result.getSpatialCoverage() != null) {
            Polygon2D polygon2D = new Polygon2D();
            double[][][] coordinates = result.getSpatialCoverage().getCoordinates();
            for (double[] point : coordinates[0]) {
                polygon2D.append(point[0], point[1]);
            }
            product.setGeometry(polygon2D.toWKT());
            if (this.filter != null && this.filter.test(product)) {
                return null;
            }
        }
        product.setFormatType(DataFormat.RASTER);
        product.setSensorType(SensorType.OPTICAL);
        product.setPixelType(PixelType.UINT16);
        product.setProductType("HYPERION");
        product.setSatelliteName("HYPERION");
        product.setId(result.getEntityId());
        product.setName(result.getDisplayId());
        try {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), firstFormat));
        } catch (DateTimeParseException e) {
            product.setAcquisitionDate(LocalDateTime.parse(result.getTemporalCoverage().getStartDate(), secondFormat));
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
