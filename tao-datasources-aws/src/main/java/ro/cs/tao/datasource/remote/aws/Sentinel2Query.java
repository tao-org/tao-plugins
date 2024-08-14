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
package ro.cs.tao.datasource.remote.aws;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.param.CommonParameterNames;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.aws.internal.AwsResult;
import ro.cs.tao.datasource.remote.aws.internal.IntermediateParser;
import ro.cs.tao.datasource.remote.aws.internal.ManifestSizeParser;
import ro.cs.tao.datasource.util.Constants;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.products.sentinels.Sentinel2TileExtent;
import ro.cs.tao.products.sentinels.SentinelProductHelper;
import ro.cs.tao.serialization.CRSAdapter;
import ro.cs.tao.utils.DateUtils;
import ro.cs.tao.utils.HttpMethod;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
class Sentinel2Query extends DataQuery {
    private static final String S2_SEARCH_URL_SUFFIX = "?delimiter=/&prefix=";
    private static final String COLLECTION_PARAMETER_NAME = "collection";
    private static final String dateFormatString = new SimpleDateFormat("yyyy-MM-dd").toPattern();
    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter timestampDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final DateTimeFormatter nameDateFormat = DateUtils.getFormatterAtUTC("yyyyMMdd'T'HHmmss");

    Sentinel2Query(DataSource source) {
        super(source, "Sentinel2");
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        QueryParameter currentParameter = this.parameters.get(CommonParameterNames.PLATFORM);
        if (currentParameter == null) {
            currentParameter = createParameter(CommonParameterNames.PLATFORM, String.class, "Sentinel-2");
            this.parameters.put(CommonParameterNames.PLATFORM, currentParameter);
        } else {
            if (!"Sentinel-2".equals(currentParameter.getValueAsString())) {
                throw new QueryException(String.format("Wrong [%s] parameter", CommonParameterNames.PLATFORM));
            }
        }
        Map<String, EOProduct> results = new LinkedHashMap<>();
        try {
            String sensingStart, sensingEnd;
            double cloudFilter = 100.01;
            int relativeOrbit = 0;

            Set<String> tiles = new HashSet<>();
            currentParameter = this.parameters.get(CommonParameterNames.TILE);
            if (currentParameter != null) {
                tiles.add(currentParameter.getValueAsString());
            } else if ((currentParameter = this.parameters.get(CommonParameterNames.FOOTPRINT)) != null) {
                Object value = currentParameter.getValue();
                Polygon2D pValue;
                if (value instanceof Polygon2D) {
                    pValue = (Polygon2D) currentParameter.getValue();
                } else {
                    pValue = Polygon2D.fromWKT(value.toString());
                }
                tiles.addAll(Sentinel2TileExtent.getInstance().intersectingTiles(pValue));
            } else {
                throw new QueryException(String.format("Either [%s] or [%s] have to be given.",
                        CommonParameterNames.TILE, CommonParameterNames.FOOTPRINT));
            }

            Calendar startDate = Calendar.getInstance();
            Calendar endDate = Calendar.getInstance();
            LocalDate todayDate = LocalDate.now();

            currentParameter = this.parameters.get(CommonParameterNames.START_DATE);
            if (currentParameter != null) {
                if (currentParameter.getValue() != null) {
                    sensingStart = currentParameter.getValueAsFormattedDate(dateFormatString);
                } else {
                    if (currentParameter.getMinValue() != null) {
                        sensingStart = currentParameter.getMinValueAsFormattedDate(dateFormatString);
                    } else {
                        sensingStart = todayDate.minusDays(30).format(dateFormat);
                    }
                }
            } else {
                sensingStart = todayDate.minusDays(30).format(dateFormat);
            }
            startDate.setTime(Date.from(LocalDate.parse(sensingStart, dateFormat).atStartOfDay(ZoneId.of("UTC")).toInstant()));
            currentParameter = this.parameters.get(CommonParameterNames.END_DATE);
            if (currentParameter != null) {
                if (currentParameter.getValue() != null) {
                    sensingEnd = currentParameter.getValueAsFormattedDate(dateFormatString);
                } else {
                    if (currentParameter.getMaxValue() != null) {
                        sensingEnd = currentParameter.getMaxValueAsFormattedDate(dateFormatString);
                    } else {
                        sensingEnd = todayDate.format(dateFormat);
                    }
                }
            } else {
                sensingEnd = todayDate.format(dateFormat);
            }
            endDate.setTime(Date.from(LocalDate.parse(sensingEnd, dateFormat).atStartOfDay(ZoneId.of("UTC")).toInstant()));
            //http://sentinel-s2-l1c.s3.amazonaws.com/?delimiter=/&prefix=tiles/15/R/TM/

            currentParameter = this.parameters.get(CommonParameterNames.CLOUD_COVER);
            if (currentParameter != null) {
                cloudFilter = currentParameter.getValueAsDouble();
            }

            currentParameter = this.parameters.get(CommonParameterNames.RELATIVE_ORBIT);
            if (currentParameter != null) {
                relativeOrbit = currentParameter.getValueAsInt();
            }

            int yearStart = startDate.get(Calendar.YEAR);
            int monthStart = startDate.get(Calendar.MONTH) + 1;
            int dayStart = startDate.get(Calendar.DAY_OF_MONTH);
            int yearEnd = endDate.get(Calendar.YEAR);
            int monthEnd = endDate.get(Calendar.MONTH) + 1;
            int dayEnd = endDate.get(Calendar.DAY_OF_MONTH);
            for (String tile : tiles) {
                if (this.limit > 0 && this.limit <= results.size()) {
                    break;
                }
                String utmCode = tile.substring(0, 2);
                String latBand = tile.substring(2, 3);
                String square = tile.substring(3, 5);
                String tileUrl = getConnectionString() + utmCode +
                        Constants.URL_SEPARATOR + latBand + Constants.URL_SEPARATOR +
                        square + Constants.URL_SEPARATOR;
                for (int year = yearStart; year <= yearEnd; year++) {
                    if (this.limit > 0 && this.limit <= results.size()) {
                        break;
                    }
                    String yearUrl = tileUrl + String.valueOf(year) + Constants.URL_SEPARATOR;
                    AwsResult yearResult = IntermediateParser.parse(((AWSDataSource) this.source).getS3ResponseAsString(HttpMethod.GET, yearUrl));
                    if (yearResult.getCommonPrefixes() != null) {
                        Set<Integer> months = yearResult.getCommonPrefixes().stream()
                                .map(p -> {
                                    String tmp = p.replace(yearResult.getPrefix(), "");
                                    return Integer.parseInt(tmp.substring(0, tmp.indexOf(yearResult.getDelimiter())));
                                }).collect(Collectors.toSet());
                        int monthS = year == yearStart ? monthStart : 1;
                        int monthE = year == yearEnd ? monthEnd : 12;
                        for (int month = monthS; month <= monthE; month++) {
                            if (this.limit > 0 && this.limit <= results.size()) {
                                break;
                            }
                            if (months.contains(month)) {
                                String monthUrl = yearUrl + month + Constants.URL_SEPARATOR;
                                AwsResult monthResult = IntermediateParser.parse(((AWSDataSource) this.source).getS3ResponseAsString(HttpMethod.GET, monthUrl));
                                if (monthResult.getCommonPrefixes() != null) {
                                    Set<Integer> days = monthResult.getCommonPrefixes().stream()
                                            .map(p -> {
                                                String tmp = p.replace(monthResult.getPrefix(), "");
                                                return Integer.parseInt(tmp.substring(0, tmp.indexOf(monthResult.getDelimiter())));
                                            }).collect(Collectors.toSet());
                                    int dayS = month == monthS ? dayStart : 1;
                                    Calendar calendar = new Calendar.Builder().setDate(year, month + 1, 1).build();
                                    calendar.add(Calendar.DAY_OF_MONTH, -1);
                                    int dayE = month == monthE ? dayEnd : calendar.get(Calendar.DAY_OF_MONTH);
                                    for (int day = dayS; day <= dayE; day++) {
                                        if (this.limit > 0 && this.limit <= results.size()) {
                                            break;
                                        }
                                        if (days.contains(day)) {
                                            String dayUrl = monthUrl + day + Constants.URL_SEPARATOR;
                                            AwsResult dayResult = IntermediateParser.parse(((AWSDataSource) this.source).getS3ResponseAsString(HttpMethod.GET, dayUrl));
                                            if (dayResult.getCommonPrefixes() != null) {
                                                Set<Integer> sequences = dayResult.getCommonPrefixes().stream()
                                                        .map(p -> {
                                                            String tmp = p.replace(dayResult.getPrefix(), "");
                                                            return Integer.parseInt(tmp.substring(0, tmp.indexOf(dayResult.getDelimiter())));
                                                        }).collect(Collectors.toSet());
                                                for (int sequence : sequences) {
                                                    if (this.limit > 0 && this.limit <= results.size()) {
                                                        break;
                                                    }
                                                    String jsonTile = dayUrl + sequence + Constants.URL_SEPARATOR + "tileInfo.json";
                                                    jsonTile = jsonTile.replace(S2_SEARCH_URL_SUFFIX, "");
                                                    EOProduct product = new EOProduct();
                                                    product.setProductType("Sentinel2");
                                                    double clouds = getTileCloudPercentage(jsonTile, product);
                                                    if (clouds > cloudFilter) {
                                                        logger.fine(String.format("Tile %s from %s has %.2f %% clouds",
                                                                tile,
                                                                LocalDate.of(year, month - 1, day).format(dateFormat),
                                                                clouds));
                                                    } else {
                                                        String jsonProduct = dayUrl + sequence + Constants.URL_SEPARATOR + "productInfo.json";
                                                        jsonProduct = jsonProduct.replace("?delimiter=/&prefix=", "");
                                                        parseProductJson(jsonProduct, product);
                                                        String manifest = product.getLocation() +
                                                                Constants.URL_SEPARATOR + "manifest.safe";
                                                        parseManifest(manifest, product);
                                                        if (relativeOrbit == 0 ||
                                                                product.getName().contains("_R" + String.format("%03d", relativeOrbit))) {
                                                            String processingDate = SentinelProductHelper.create(product.getName()).getProcessingDate();
                                                            if (processingDate != null) {
                                                                product.setProcessingDate(LocalDateTime.parse(processingDate, nameDateFormat));
                                                            }
                                                            if (this.coverageFilter == null || !this.coverageFilter.test(product)) {
                                                                results.put(product.getName(), product);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.warning(ex.getMessage());
        }
        logger.info(String.format("Query returned %s products", results.size()));
        return new ArrayList<>(results.values());
    }

    @Override
    protected long getCountImpl() {
        long count = 0;
        QueryParameter currentParameter = this.parameters.get(CommonParameterNames.PLATFORM);
        if (currentParameter == null) {
            currentParameter = createParameter(CommonParameterNames.PLATFORM, String.class, "Sentinel-2");
            this.parameters.put(CommonParameterNames.PLATFORM, currentParameter);
        } else {
            if (!"Sentinel-2".equals(currentParameter.getValueAsString())) {
                throw new QueryException(String.format("Wrong [%s] parameter", CommonParameterNames.PLATFORM));
            }
        }

        try {
            String sensingStart, sensingEnd;

            Set<String> tiles = new HashSet<>();
            currentParameter = this.parameters.get(CommonParameterNames.TILE);
            if (currentParameter != null) {
                tiles.add(currentParameter.getValueAsString());
            } else if ((currentParameter = this.parameters.get(CommonParameterNames.FOOTPRINT)) != null) {
                Polygon2D aoi = (Polygon2D) currentParameter.getValue();
                tiles.addAll(Sentinel2TileExtent.getInstance().intersectingTiles(aoi.getBounds2D()));
            } else {
                throw new QueryException(String.format("Either [%s] or [%s] have to be given.",
                        CommonParameterNames.TILE, CommonParameterNames.FOOTPRINT));
            }

            Calendar startDate = Calendar.getInstance();
            Calendar endDate = Calendar.getInstance();
            LocalDate todayDate = LocalDate.now();

            currentParameter = this.parameters.get(CommonParameterNames.START_DATE);
            if (currentParameter != null) {
                if (currentParameter.getValue() != null) {
                    sensingStart = currentParameter.getValueAsFormattedDate(dateFormatString);
                } else {
                    if (currentParameter.getMinValue() != null) {
                        sensingStart = currentParameter.getMinValueAsFormattedDate(dateFormatString);
                    } else {
                        sensingStart = todayDate.minusDays(30).format(dateFormat);
                    }
                }
            } else {
                sensingStart = todayDate.minusDays(30).format(dateFormat);
            }
            startDate.setTime(Date.from(LocalDate.parse(sensingStart, dateFormat).atStartOfDay(ZoneId.of("UTC")).toInstant()));
            currentParameter = this.parameters.get(CommonParameterNames.END_DATE);
            if (currentParameter != null) {
                if (currentParameter.getValue() != null) {
                    sensingEnd = currentParameter.getValueAsFormattedDate(dateFormatString);
                } else {
                    if (currentParameter.getMaxValue() != null) {
                        sensingEnd = currentParameter.getMaxValueAsFormattedDate(dateFormatString);
                    } else {
                        sensingEnd = todayDate.format(dateFormat);
                    }
                }
            } else {
                sensingEnd = todayDate.format(dateFormat);
            }
            endDate.setTime(Date.from(LocalDate.parse(sensingEnd, dateFormat).atStartOfDay(ZoneId.of("UTC")).toInstant()));
            //http://sentinel-s2-l1c.s3.amazonaws.com/?delimiter=/&prefix=tiles/15/R/TM/

            /*currentParameter = this.parameters.get("cloudcoverpercentage");
            if (currentParameter != null) {
                cloudFilter = currentParameter.getValueAsDouble();
            }

            currentParameter = this.parameters.get("relativeOrbitNumber");
            if (currentParameter != null) {
                relativeOrbit = currentParameter.getValueAsInt();
            }*/

            int yearStart = startDate.get(Calendar.YEAR);
            int monthStart = startDate.get(Calendar.MONTH) + 1;
            int dayStart = startDate.get(Calendar.DAY_OF_MONTH);
            int yearEnd = endDate.get(Calendar.YEAR);
            int monthEnd = endDate.get(Calendar.MONTH) + 1;
            int dayEnd = endDate.get(Calendar.DAY_OF_MONTH);
            for (String tile : tiles) {
                if (this.limit > 0 && this.limit <= count) {
                    break;
                }
                String utmCode = tile.substring(0, 2);
                String latBand = tile.substring(2, 3);
                String square = tile.substring(3, 5);
                String tileUrl = getConnectionString() + utmCode +
                        Constants.URL_SEPARATOR + latBand + Constants.URL_SEPARATOR +
                        square + Constants.URL_SEPARATOR;
                for (int year = yearStart; year <= yearEnd; year++) {
                    if (this.limit > 0 && this.limit <= count) {
                        break;
                    }
                    String yearUrl = tileUrl + String.valueOf(year) + Constants.URL_SEPARATOR;
                    AwsResult yearResult = IntermediateParser.parse(((AWSDataSource) this.source).getS3ResponseAsString(HttpMethod.GET, yearUrl));
                    if (yearResult.getCommonPrefixes() != null) {
                        Set<Integer> months = yearResult.getCommonPrefixes().stream()
                                .map(p -> {
                                    String tmp = p.replace(yearResult.getPrefix(), "");
                                    return Integer.parseInt(tmp.substring(0, tmp.indexOf(yearResult.getDelimiter())));
                                }).collect(Collectors.toSet());
                        int monthS = year == yearStart ? monthStart : 1;
                        int monthE = year == yearEnd ? monthEnd : 12;
                        for (int month = monthS; month <= monthE; month++) {
                            if (this.limit > 0 && this.limit <= count) {
                                break;
                            }
                            if (months.contains(month)) {
                                String monthUrl = yearUrl + String.valueOf(month) + Constants.URL_SEPARATOR;
                                AwsResult monthResult = IntermediateParser.parse(((AWSDataSource) this.source).getS3ResponseAsString(HttpMethod.GET, monthUrl));
                                if (monthResult.getCommonPrefixes() != null) {
                                    Set<Integer> days = monthResult.getCommonPrefixes().stream()
                                            .map(p -> {
                                                String tmp = p.replace(monthResult.getPrefix(), "");
                                                return Integer.parseInt(tmp.substring(0, tmp.indexOf(monthResult.getDelimiter())));
                                            }).collect(Collectors.toSet());
                                    int dayS = month == monthS ? dayStart : 1;
                                    Calendar calendar = new Calendar.Builder().setDate(year, month + 1, 1).build();
                                    calendar.add(Calendar.DAY_OF_MONTH, -1);
                                    int dayE = month == monthE ? dayEnd : calendar.get(Calendar.DAY_OF_MONTH);
                                    for (int day = dayS; day <= dayE; day++) {
                                        if (days.contains(day)) {
                                            String dayUrl = monthUrl + day + Constants.URL_SEPARATOR;
                                            AwsResult dayResult = IntermediateParser.parse(((AWSDataSource) this.source).getS3ResponseAsString(HttpMethod.GET, dayUrl));
                                            if (dayResult.getCommonPrefixes() != null) {
                                                count += dayResult.getCommonPrefixes().stream()
                                                        .map(p -> {
                                                            String tmp = p.replace(dayResult.getPrefix(), "");
                                                            return Integer.parseInt(tmp.substring(0, tmp.indexOf(dayResult.getDelimiter())));
                                                        }).count();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.warning(ex.getMessage());
        }
        return count;
    }

    private void parseProductJson(String jsonUrl, EOProduct product) throws Exception {
        try (JsonReader reader = Json.createReader(((AWSDataSource) this.source).buildS3Connection(HttpMethod.GET, jsonUrl).getInputStream())) {
            JsonObject obj = reader.readObject();
            product.setFormatType(DataFormat.RASTER);
            product.setSensorType(SensorType.OPTICAL);
            product.setPixelType(PixelType.UINT16);
            product.setName(obj.getString("name"));
            product.setId(obj.getString("id"));
            product.setLocation(getConnectionString()
                    .replace(S2_SEARCH_URL_SUFFIX + "tiles/", "") + obj.getString("path"));
            product.setAcquisitionDate(LocalDateTime.parse(obj.getString("timestamp"), timestampDateFormat));
            JsonObject tile = obj.getJsonArray("tiles").getJsonObject(0);
            String utmCode = String.format("%02d", tile.getInt("utmZone")) +
                    tile.getString("latitudeBand") + tile.getString("gridSquare");
            product.addAttribute("utmCode", utmCode);
            product.setWidth(-1);
            product.setHeight(-1);
        }
    }

    private void parseManifest(String manifestUrl, EOProduct product) {
        try (InputStream inputStream = ((AWSDataSource) this.source).buildS3Connection(HttpMethod.GET, manifestUrl).getInputStream()) {
            long size = ManifestSizeParser.parse(new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n")));
            product.setApproximateSize(size);
        } catch (Exception ex) {
            logger.warning(ex.getMessage());
        }
    }

    private double getTileCloudPercentage(String jsonUrl, EOProduct product) throws IOException {
        if (product == null) {
            product = new EOProduct();
        }
        try (InputStream inputStream = ((AWSDataSource) this.source).buildS3Connection(HttpMethod.GET, jsonUrl).getInputStream();
             JsonReader reader = Json.createReader(inputStream)) {
            JsonObject obj = reader.readObject();
            double clouds = obj.getJsonNumber("cloudyPixelPercentage").doubleValue();
            product.addAttribute("cloudcoverpercentage", String.valueOf(clouds));
            try {
                final String initialCrsCode = obj.getJsonObject("tileGeometry")
                        .getJsonObject("crs")
                        .getJsonObject("properties")
                        .getString("name");
                final CoordinateReferenceSystem initialCrs = new CRSAdapter().marshal(initialCrsCode);
                final CoordinateReferenceSystem targetCrs = CRS.decode("EPSG:4326", true);
                MathTransform mathTransform;
                try {
                    mathTransform = CRS.findMathTransform(initialCrs, targetCrs);
                } catch (Exception ex) {
                    // fall back to lenient transform if Bursa Wolf parameters are not provided
                    mathTransform = CRS.findMathTransform(initialCrs, targetCrs, true);
                }

                JsonArray coords = obj.getJsonObject("tileGeometry").getJsonArray("coordinates").getJsonArray(0);
                Polygon2D polygon2D = new Polygon2D();
                final int latIdx = 1, lonIdx = 0;
                for (int i = 0; i < 5; i++) {
                    final JsonArray array = coords.getJsonArray(i);
                    Coordinate fromPoint = new Coordinate(array.getJsonNumber(lonIdx).doubleValue(),
                            array.getJsonNumber(latIdx).doubleValue());
                    Coordinate toPoint = new Coordinate();
                    JTS.transform(fromPoint, toPoint, mathTransform);
                    polygon2D.append(toPoint.y, toPoint.x);
                    //polygon2D.append(toPoint.x, toPoint.y);
                }
                product.setGeometry(polygon2D.toWKT());
                product.setCrs("EPSG:4326");
                //}
            } catch (Exception e) {
                e.printStackTrace();
            }
            return clouds;
        }
    }

    @Override
    public String defaultId() { return "Sentinel2AWSQuery";}

    private String getConnectionString() {
        final String collection = this.parameters.get(COLLECTION_PARAMETER_NAME).getValueAsString();
        final String connectionString = this.source.getConnectionString();
        return connectionString.replace(COLLECTION_PARAMETER_NAME, collection);
    }

}
