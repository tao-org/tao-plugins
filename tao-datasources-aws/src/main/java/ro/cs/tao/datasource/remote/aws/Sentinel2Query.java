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
package ro.cs.tao.datasource.remote.aws;

import ro.cs.tao.datasource.DataQuery;
import ro.cs.tao.datasource.DataSource;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.param.QueryParameter;
import ro.cs.tao.datasource.remote.DownloadStrategy;
import ro.cs.tao.datasource.remote.aws.internal.AwsResult;
import ro.cs.tao.datasource.remote.aws.internal.IntermediateParser;
import ro.cs.tao.datasource.remote.aws.internal.ManifestSizeParser;
import ro.cs.tao.datasource.util.NetUtils;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.products.sentinels.Sentinel2TileExtent;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
class Sentinel2Query extends DataQuery {
    private static final String S2_SEARCH_URL_SUFFIX = "?delimiter=/&prefix=";
    private static final String dateFormatString = new SimpleDateFormat("yyyy-MM-dd").toPattern();
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private static final DateTimeFormatter fileDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    Sentinel2Query(DataSource source) {
        super(source, "Sentinel2");
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        QueryParameter currentParameter = this.parameters.get("platformName");
        if (currentParameter == null) {
            currentParameter = createParameter("platformName", String.class, "Sentinel-2");
            this.parameters.put("platformName", currentParameter);
        } else {
            if (!"Sentinel-2".equals(currentParameter.getValueAsString())) {
                throw new QueryException("Wrong [platformName] parameter");
            }
        }
        Map<String, EOProduct> results = new LinkedHashMap<>();
        try {
            String sensingStart, sensingEnd;
            double cloudFilter = 100.01;
            int relativeOrbit = 0;

            Set<String> tiles = new HashSet<>();
            currentParameter = this.parameters.get("tileId");
            if (currentParameter != null) {
                tiles.add(currentParameter.getValueAsString());
            } else if ((currentParameter = this.parameters.get("footprint")) != null) {
                Object value = currentParameter.getValue();
                Polygon2D pValue;
                if (value instanceof Polygon2D) {
                    pValue = (Polygon2D) currentParameter.getValue();
                } else {
                    pValue = Polygon2D.fromWKT(value.toString());
                }
                //tiles.addAll(Sentinel2TileExtent.getInstance().intersectingTiles(pValue.getBounds2D()));
                tiles.addAll(Sentinel2TileExtent.getInstance().intersectingTiles(pValue));
            } else {
                throw new QueryException("Either [tileId] or [footprint] have to be given.");
            }

            Calendar startDate = Calendar.getInstance();
            Calendar endDate = Calendar.getInstance();
            LocalDate todayDate = LocalDate.now();

            currentParameter = this.parameters.get("beginPosition");
            if (currentParameter != null) {
                if (currentParameter.getValue() != null) {
                    sensingStart = currentParameter.getValueAsFormattedDate(dateFormatString);
                } else {
                    if (currentParameter.getMinValue() != null) {
                        sensingStart = currentParameter.getMinValueAsFormattedDate(dateFormatString);
                    } else {
                        sensingStart = todayDate.minusDays(30).format(fileDateFormat);
                    }
                }
            } else {
                sensingStart = todayDate.minusDays(30).format(fileDateFormat);
            }
            startDate.setTime(dateFormat.parse(sensingStart));
            currentParameter = this.parameters.get("endPosition");
            if (currentParameter != null) {
                if (currentParameter.getValue() != null) {
                    sensingEnd = currentParameter.getValueAsFormattedDate(dateFormatString);
                } else {
                    if (currentParameter.getMaxValue() != null) {
                        sensingEnd = currentParameter.getMaxValueAsFormattedDate(dateFormatString);
                    } else {
                        sensingEnd = todayDate.format(fileDateFormat);
                    }
                }
            } else {
                sensingEnd = todayDate.format(fileDateFormat);
            }
            endDate.setTime(dateFormat.parse(sensingEnd));
            //http://sentinel-s2-l1c.s3.amazonaws.com/?delimiter=/&prefix=tiles/15/R/TM/

            currentParameter = this.parameters.get("cloudcoverpercentage");
            if (currentParameter != null) {
                cloudFilter = currentParameter.getValueAsDouble();
            }

            currentParameter = this.parameters.get("relativeOrbitNumber");
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
                String tileUrl = this.source.getConnectionString() + utmCode +
                        DownloadStrategy.URL_SEPARATOR + latBand + DownloadStrategy.URL_SEPARATOR +
                        square + DownloadStrategy.URL_SEPARATOR;
                for (int year = yearStart; year <= yearEnd; year++) {
                    if (this.limit > 0 && this.limit <= results.size()) {
                        break;
                    }
                    String yearUrl = tileUrl + String.valueOf(year) + DownloadStrategy.URL_SEPARATOR;
                    AwsResult yearResult = IntermediateParser.parse(NetUtils.getResponseAsString(yearUrl));
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
                                String monthUrl = yearUrl + String.valueOf(month) + DownloadStrategy.URL_SEPARATOR;
                                AwsResult monthResult = IntermediateParser.parse(NetUtils.getResponseAsString(monthUrl));
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
                                            String dayUrl = monthUrl + String.valueOf(day) + DownloadStrategy.URL_SEPARATOR;
                                            AwsResult dayResult = IntermediateParser.parse(NetUtils.getResponseAsString(dayUrl));
                                            if (dayResult.getCommonPrefixes() != null) {
                                                Set<Integer> sequences = dayResult.getCommonPrefixes().stream()
                                                        .map(p -> {
                                                            String tmp = p.replace(dayResult.getPrefix(), "");
                                                            return Integer.parseInt(tmp.substring(0, tmp.indexOf(dayResult.getDelimiter())));
                                                        }).collect(Collectors.toSet());
                                                for (int sequence : sequences) {
                                                    String jsonTile = dayUrl + String.valueOf(sequence) +
                                                            DownloadStrategy.URL_SEPARATOR + "tileInfo.json";
                                                    jsonTile = jsonTile.replace(S2_SEARCH_URL_SUFFIX, "");
                                                    EOProduct product = new EOProduct();
                                                    product.setProductType("Sentinel2");
                                                    double clouds = getTileCloudPercentage(jsonTile, product);
                                                    if (clouds > cloudFilter) {
                                                        Calendar instance = new Calendar.Builder().setDate(year, month - 1, day).build();
                                                        logger.fine(String.format("Tile %s from %s has %.2f %% clouds",
                                                                                     tile,
                                                                                     dateFormat.format(instance.getTime()),
                                                                                     clouds));
                                                    } else {
                                                        String jsonProduct = dayUrl + String.valueOf(sequence) +
                                                                DownloadStrategy.URL_SEPARATOR + "productInfo.json";
                                                        jsonProduct = jsonProduct.replace("?delimiter=/&prefix=", "");
                                                        parseProductJson(jsonProduct, product);
                                                        String manifest = product.getLocation() +
                                                                DownloadStrategy.URL_SEPARATOR + "manifest.safe";
                                                        parseManifest(manifest, product);
                                                        if (relativeOrbit == 0 ||
                                                                product.getName().contains("_R" + String.format("%03d", relativeOrbit))) {
                                                            if (this.limit > 0 && this.limit <= results.size()) {
                                                                break;
                                                            }
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
        } catch (Exception ex) {
            logger.warning(ex.getMessage());
        }
        logger.info(String.format("Query returned %s products", results.size()));
        return new ArrayList<>(results.values());
    }

    @Override
    protected long getCountImpl() {
        long count = 0;
        QueryParameter currentParameter = this.parameters.get("platformName");
        if (currentParameter == null) {
            currentParameter = createParameter("platformName", String.class, "Sentinel-2");
            this.parameters.put("platformName", currentParameter);
        } else {
            if (!"Sentinel-2".equals(currentParameter.getValueAsString())) {
                throw new QueryException("Wrong [platformName] parameter");
            }
        }

        try {
            String sensingStart, sensingEnd;

            Set<String> tiles = new HashSet<>();
            currentParameter = this.parameters.get("tileId");
            if (currentParameter != null) {
                tiles.add(currentParameter.getValueAsString());
            } else if ((currentParameter = this.parameters.get("footprint")) != null) {
                Polygon2D aoi = (Polygon2D) currentParameter.getValue();
                tiles.addAll(Sentinel2TileExtent.getInstance().intersectingTiles(aoi.getBounds2D()));
            } else {
                throw new QueryException("Either [tileId] or [footprint] have to be given.");
            }

            Calendar startDate = Calendar.getInstance();
            Calendar endDate = Calendar.getInstance();
            LocalDate todayDate = LocalDate.now();

            currentParameter = this.parameters.get("beginPosition");
            if (currentParameter != null) {
                if (currentParameter.getValue() != null) {
                        sensingStart = currentParameter.getValueAsFormattedDate(dateFormatString);
                } else {
                    if (currentParameter.getMinValue() != null) {
                        sensingStart = currentParameter.getMinValueAsFormattedDate(dateFormatString);
                    } else {
                        sensingStart = todayDate.minusDays(30).format(fileDateFormat);
                    }
                }
            } else {
                sensingStart = todayDate.minusDays(30).format(fileDateFormat);
            }
            startDate.setTime(dateFormat.parse(sensingStart));
            currentParameter = this.parameters.get("endPosition");
            if (currentParameter != null) {
                if (currentParameter.getValue() != null) {
                    sensingEnd = currentParameter.getValueAsFormattedDate(dateFormatString);
                } else {
                    if (currentParameter.getMaxValue() != null) {
                        sensingEnd = currentParameter.getMaxValueAsFormattedDate(dateFormatString);
                    } else {
                        sensingEnd = todayDate.format(fileDateFormat);
                    }
                }
            } else {
                sensingEnd = todayDate.format(fileDateFormat);
            }
            endDate.setTime(dateFormat.parse(sensingEnd));
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
                String tileUrl = this.source.getConnectionString() + utmCode +
                        DownloadStrategy.URL_SEPARATOR + latBand + DownloadStrategy.URL_SEPARATOR +
                        square + DownloadStrategy.URL_SEPARATOR;
                for (int year = yearStart; year <= yearEnd; year++) {
                    if (this.limit > 0 && this.limit <= count) {
                        break;
                    }
                    String yearUrl = tileUrl + String.valueOf(year) + DownloadStrategy.URL_SEPARATOR;
                    AwsResult yearResult = IntermediateParser.parse(NetUtils.getResponseAsString(yearUrl));
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
                                String monthUrl = yearUrl + String.valueOf(month) + DownloadStrategy.URL_SEPARATOR;
                                AwsResult monthResult = IntermediateParser.parse(NetUtils.getResponseAsString(monthUrl));
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
                                            String dayUrl = monthUrl + String.valueOf(day) + DownloadStrategy.URL_SEPARATOR;
                                            AwsResult dayResult = IntermediateParser.parse(NetUtils.getResponseAsString(dayUrl));
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
        try (JsonReader reader = Json.createReader(new URI(jsonUrl).toURL().openStream())) {
            JsonObject obj = reader.readObject();
            product.setFormatType(DataFormat.RASTER);
            product.setSensorType(SensorType.OPTICAL);
            product.setPixelType(PixelType.UINT16);
            product.setName(obj.getString("name"));
            product.setId(obj.getString("id"));
            product.setLocation(this.source.getConnectionString()
                                        .replace(S2_SEARCH_URL_SUFFIX + "tiles/", "") + obj.getString("path"));
            product.setAcquisitionDate(dateFormat.parse(obj.getString("timestamp")));
            JsonObject tile = obj.getJsonArray("tiles").getJsonObject(0);
            String utmCode = String.format("%02d", tile.getInt("utmZone")) +
                    tile.getString("latitudeBand") + tile.getString("gridSquare");
            product.addAttribute("utmCode", utmCode);
            product.setWidth(-1);
            product.setHeight(-1);
        }
    }

    private void parseManifest(String manifestUrl, EOProduct product) {
        try (InputStream inputStream = new URI(manifestUrl).toURL().openStream()) {
            long size = ManifestSizeParser.parse(new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n")));
            product.setApproximateSize(size);
        } catch (Exception ex) {
            logger.warning(ex.getMessage());
        }
    }

    private double getTileCloudPercentage(String jsonUrl, EOProduct product) throws IOException, URISyntaxException {
        JsonReader reader = null;
        if (product == null) {
            product = new EOProduct();
        }
        try (InputStream inputStream = new URI(jsonUrl).toURL().openStream()) {
            reader = Json.createReader(inputStream);
            JsonObject obj = reader.readObject();
            double clouds = obj.getJsonNumber("cloudyPixelPercentage").doubleValue();
            product.addAttribute("cloudcoverpercentage", String.valueOf(clouds));
            try {
                product.setCrs(obj.getJsonObject("tileGeometry")
                                .getJsonObject("crs")
                                .getJsonObject("properties")
                                .getString("name"));
                //if (product.getGeometry() == null) {
                    JsonArray coords = obj.getJsonObject("tileGeometry").getJsonArray("coordinates").getJsonArray(0);
                    Polygon2D polygon2D = new Polygon2D();
                    polygon2D.append(coords.getJsonArray(0).getJsonNumber(0).doubleValue(),
                                     coords.getJsonArray(0).getJsonNumber(1).doubleValue());
                    polygon2D.append(coords.getJsonArray(1).getJsonNumber(0).doubleValue(),
                                     coords.getJsonArray(1).getJsonNumber(1).doubleValue());
                    polygon2D.append(coords.getJsonArray(2).getJsonNumber(0).doubleValue(),
                                     coords.getJsonArray(2).getJsonNumber(1).doubleValue());
                    polygon2D.append(coords.getJsonArray(3).getJsonNumber(0).doubleValue(),
                                     coords.getJsonArray(3).getJsonNumber(1).doubleValue());
                    polygon2D.append(coords.getJsonArray(4).getJsonNumber(0).doubleValue(),
                                     coords.getJsonArray(4).getJsonNumber(1).doubleValue());
                    product.setGeometry(polygon2D.toWKT());
                //}
            } catch (Exception e) {
                e.printStackTrace();
            }
            return clouds;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    @Override
    public String defaultName() {
        return "Sentinel2AWSQuery";
    }

}
