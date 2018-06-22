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
import ro.cs.tao.datasource.util.NetUtils;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.util.Conversions;
import ro.cs.tao.products.landsat.Landsat8ProductHelper;
import ro.cs.tao.products.landsat.Landsat8TileExtent;
import ro.cs.tao.products.landsat.LandsatProduct;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Cosmin Cara
 */
class Landsat8Query extends DataQuery {
    private static final String L8_SEARCH_URL_SUFFIX = "?delimiter=/&prefix=";
    private static final DateTimeFormatter fileDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private final Logger logger = Logger.getLogger(Landsat8Query.class.getSimpleName());

    Landsat8Query(DataSource source) {
        super(source, "Landsat8");
    }

    @Override
    protected List<EOProduct> executeImpl() throws QueryException {
        QueryParameter currentParameter = this.parameters.get("platformName");
        if (currentParameter == null) {
            currentParameter = createParameter("platformName", String.class, "Landsat-8");
            this.parameters.put("platformName", currentParameter);
        } else {
            if (!"Landsat-8".equals(currentParameter.getValueAsString())) {
                throw new QueryException("Wrong [platformName] parameter");
            }
        }
        String baseUrl = this.source.getConnectionString();
        String alternateUrl = this.source.getAlternateConnectionString();
        currentParameter = this.parameters.get("collection");
        boolean preCollection = false;
        if (currentParameter != null) {
            if (LandsatCollection.PRE_COLLECTION.equals(
                    Enum.valueOf(LandsatCollection.class, currentParameter.getValueAsString()))) {
                baseUrl = baseUrl.replace("c1/", "");
                preCollection = true;
            }
        }
        Map<String, EOProduct> results = new LinkedHashMap<>();
        try {
            String sensingStart, sensingEnd;
            LandsatProduct productType;
            double cloudFilter = 100.;

            LocalDate todayDate = LocalDate.now();

            //http://sentinel-s2-l1c.s3.amazonaws.com/?delimiter=/&prefix=c1/L8/
            Calendar startDate = Calendar.getInstance();
            currentParameter = this.parameters.get("sensingStart");
            if (currentParameter != null) {
                if (currentParameter.getValue() != null) {
                    sensingStart = currentParameter.getValueAsFormattedDate(dateFormat.toPattern());
                } else {
                    sensingStart = currentParameter.getMinValueAsFormattedDate(dateFormat.toPattern());
                }
            } else {
                sensingStart = todayDate.minusDays(30).format(fileDateFormat);
            }
            currentParameter = this.parameters.get("sensingEnd");
            if (currentParameter != null) {
                if (currentParameter.getValue() != null) {
                    sensingEnd = currentParameter.getValueAsFormattedDate(dateFormat.toPattern());
                } else {
                    sensingEnd = currentParameter.getMaxValueAsFormattedDate(dateFormat.toPattern());
                }
            } else {
                sensingEnd = todayDate.format(fileDateFormat);
            }
            startDate.setTime(dateFormat.parse(sensingStart));
            Calendar endDate = Calendar.getInstance();
            endDate.setTime(dateFormat.parse(sensingEnd));
            currentParameter = this.parameters.get("productType");
            if (currentParameter != null) {
                productType = Enum.valueOf(LandsatProduct.class, currentParameter.getValueAsString());
            } else {
                productType = LandsatProduct.T1;
            }

            currentParameter = this.parameters.get("cloudcoverpercentage");
            if (currentParameter != null) {
                cloudFilter = currentParameter.getValueAsDouble();
            }

            Set<String> tiles = new HashSet<>();
            currentParameter = this.parameters.get("row_path");
            if (currentParameter != null) {
                tiles.add(currentParameter.getValueAsString());
            } else {
                currentParameter = this.parameters.get("path");
                if (currentParameter != null) {
                    String path = currentParameter.getValueAsString();
                    currentParameter = this.parameters.get("row");
                    if (currentParameter == null) {
                        throw new QueryException("Parameter [row] expected when [path] is set");
                    }
                    String row = currentParameter.getValueAsString();
                    tiles.add(path + row);
                } else {
                    currentParameter = this.parameters.get("row");
                    if (currentParameter != null) {
                        throw new QueryException("Parameter [path] expected when [row] is set");
                    }
                    currentParameter = this.parameters.get("footprint");
                    if (currentParameter == null) {
                        throw new QueryException("Either [footprint] or ([path] and [row]) should be provided");
                    }
                    Polygon2D aoi = (Polygon2D) currentParameter.getValue();
                    if (aoi == null || aoi.getNumPoints() == 0) {
                        throw new QueryException("The provided [footprint] is empty");
                    }
                    //tiles.addAll(Landsat8TileExtent.getInstance().intersectingTiles(aoi.getBounds2D()));
                    tiles.addAll(Landsat8TileExtent.getInstance().intersectingTiles(aoi));
                }
            }
            for (String tile : tiles) {
                String path = tile.substring(0, 3);
                String row = tile.substring(3, 6);
                String tileUrl = alternateUrl + path + DownloadStrategy.URL_SEPARATOR + row + DownloadStrategy.URL_SEPARATOR;
                final AwsResult preCollectionResult = IntermediateParser.parse(NetUtils.getResponseAsString(tileUrl));
                if (preCollectionResult.getCommonPrefixes() != null) {
                    Set<String> names = preCollectionResult.getCommonPrefixes().stream()
                            .map(p -> p.replace(preCollectionResult.getPrefix(), "").replace(preCollectionResult.getDelimiter(), ""))
                            .collect(Collectors.toSet());
                    for (String name : names) {
                        try {
                            if (preCollection || name.endsWith(productType.toString())) {
                                Landsat8ProductHelper temporaryDescriptor = new Landsat8ProductHelper(name);
                                Calendar productDate = temporaryDescriptor.getAcquisitionDate();
                                if (startDate.before(productDate) && endDate.after(productDate)) {
                                    String jsonTile = tileUrl + name + DownloadStrategy.URL_SEPARATOR + name + "_MTL.json";
                                    jsonTile = jsonTile.replace(L8_SEARCH_URL_SUFFIX, "");
                                    double clouds = getTileCloudPercentage(jsonTile);
                                    if (clouds > cloudFilter) {
                                        productDate.add(Calendar.MONTH, -1);
                                        logger.fine(String.format("Tile %s from %s has %.2f %% clouds",
                                                                  tile,
                                                                  dateFormat.format(productDate.getTime()),
                                                                  clouds));
                                    } else {
                                        EOProduct product = parseProductJson(jsonTile);
                                        if (this.limit > 0 && this.limit < results.size()) {
                                            break;
                                        }
                                        results.put(product.getName(), product);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            logger.warning(String.format("Could not parse product %s: %s", name, ex.getMessage()));
                        }
                    }
                }
                tileUrl = baseUrl + path + DownloadStrategy.URL_SEPARATOR + row + DownloadStrategy.URL_SEPARATOR;
                final AwsResult productResult = IntermediateParser.parse(NetUtils.getResponseAsString(tileUrl));
                if (productResult.getCommonPrefixes() != null) {
                    Set<String> names = productResult.getCommonPrefixes().stream()
                            .map(p -> p.replace(productResult.getPrefix(), "").replace(productResult.getDelimiter(), ""))
                            .collect(Collectors.toSet());
                    for (String name : names) {
                        try {
                            if (preCollection || name.endsWith(productType.toString())) {
                                Landsat8ProductHelper temporaryDescriptor = new Landsat8ProductHelper(name);
                                Calendar productDate = temporaryDescriptor.getAcquisitionDate();
                                if (startDate.before(productDate) && endDate.after(productDate)) {
                                    String jsonTile = tileUrl + name + DownloadStrategy.URL_SEPARATOR + name + "_MTL.json";
                                    jsonTile = jsonTile.replace(L8_SEARCH_URL_SUFFIX, "");
                                    double clouds = getTileCloudPercentage(jsonTile);
                                    if (clouds > cloudFilter) {
                                        productDate.add(Calendar.MONTH, -1);
                                        logger.fine(String.format("Tile %s from %s has %.2f %% clouds",
                                                                  tile,
                                                                  dateFormat.format(productDate.getTime()),
                                                                  clouds));
                                    } else {
                                        EOProduct product = parseProductJson(jsonTile);
                                        if (this.limit > 0 && this.limit < results.size()) {
                                            break;
                                        }
                                        results.put(product.getName(), product);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            logger.warning(String.format("Could not parse product %s: %s", name, ex.getMessage()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warning(e.getMessage());
        }
        logger.info(String.format("Query returned %s products", results.size()));
        return new ArrayList<>(results.values());
    }

    private EOProduct parseProductJson(String jsonUrl) throws Exception {
        JsonReader reader = null;
        EOProduct product;
        try (InputStream inputStream = new URI(jsonUrl).toURL().openStream()) {
            reader = Json.createReader(inputStream);
            JsonObject rootObject = reader.readObject().getJsonObject("L1_METADATA_FILE");
            JsonObject obj = rootObject.getJsonObject("METADATA_FILE_INFO");
            product = new EOProduct();
            product.setProductType("Landsat8");
            product.setId(obj.getString("LANDSAT_SCENE_ID"));
            if (obj.containsKey("LANDSAT_PRODUCT_ID")) {
                product.setName(obj.getString("LANDSAT_PRODUCT_ID"));
            } else {
                product.setName(obj.getString("LANDSAT_SCENE_ID"));
            }
            product.setFormatType(DataFormat.RASTER);
            product.setSensorType(SensorType.OPTICAL);
            obj = rootObject.getJsonObject("PRODUCT_METADATA");
            product.setAcquisitionDate(dateFormat.parse(obj.getString("DATE_ACQUIRED")));
            product.setWidth(obj.getInt("REFLECTIVE_SAMPLES"));
            product.setHeight(obj.getInt("REFLECTIVE_LINES"));
            Polygon2D footprint = new Polygon2D();
            footprint.append(obj.getJsonNumber("CORNER_UL_LAT_PRODUCT").doubleValue(),
                             obj.getJsonNumber("CORNER_UL_LON_PRODUCT").doubleValue());
            footprint.append(obj.getJsonNumber("CORNER_UR_LAT_PRODUCT").doubleValue(),
                             obj.getJsonNumber("CORNER_UR_LON_PRODUCT").doubleValue());
            footprint.append(obj.getJsonNumber("CORNER_LR_LAT_PRODUCT").doubleValue(),
                             obj.getJsonNumber("CORNER_LR_LON_PRODUCT").doubleValue());
            footprint.append(obj.getJsonNumber("CORNER_LL_LAT_PRODUCT").doubleValue(),
                             obj.getJsonNumber("CORNER_LL_LON_PRODUCT").doubleValue());
            footprint.append(obj.getJsonNumber("CORNER_UL_LAT_PRODUCT").doubleValue(),
                             obj.getJsonNumber("CORNER_UL_LON_PRODUCT").doubleValue());
            product.setGeometry(footprint.toWKT());

            obj = rootObject.getJsonObject("MIN_MAX_PIXEL_VALUE");
            product.setPixelType(Conversions.pixelTypeFromRange(obj.getInt("QUANTIZE_CAL_MIN_BAND_1"),
                                                                obj.getInt("QUANTIZE_CAL_MAX_BAND_1")));
            product.setLocation(jsonUrl.substring(0, jsonUrl.lastIndexOf("/")));

            rootObject.keySet()
                    .forEach(k -> rootObject.getJsonObject(k)
                        .forEach((key, value) -> product.addAttribute(key, value.toString())));
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return product;
    }

    private double getTileCloudPercentage(String jsonUrl) throws IOException, URISyntaxException {
        JsonReader reader = null;
        try (InputStream inputStream = new URI(jsonUrl).toURL().openStream()) {
            reader = Json.createReader(inputStream);
            JsonObject obj = reader.readObject();
            return obj.getJsonObject("L1_METADATA_FILE")
                    .getJsonObject("IMAGE_ATTRIBUTES")
                    .getJsonNumber("CLOUD_COVER").doubleValue();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    @Override
    public String defaultName() {
        return "LandsatAWSQuery";
    }
}