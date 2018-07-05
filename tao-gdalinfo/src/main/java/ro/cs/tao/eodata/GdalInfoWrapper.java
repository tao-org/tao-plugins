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

package ro.cs.tao.eodata;

import org.geotools.referencing.CRS;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.utils.executors.ProcessExecutor;

import javax.json.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GdalInfoWrapper implements MetadataInspector {

    public GdalInfoWrapper() { }

    @Override
    public Metadata getMetadata(Path productPath) throws IOException {
        if (productPath == null) {
            return null;
        }
        Executor executor = initialize(productPath.toAbsolutePath().toString());
        OutputAccumulator consumer = new OutputAccumulator();
        executor.setOutputConsumer(consumer);
        StringReader reader = null;
        Metadata metadata = null;
        try {
            if (executor.execute(true) == 0) {
                String output = consumer.getOutput();
                reader = new StringReader(output);
                JsonReader jsonReader = Json.createReader(reader);
                JsonObject root = jsonReader.readObject();
                metadata = new Metadata();
                metadata.setEntryPoint(productPath.toUri());
                metadata.setProductType(root.getString("driverLongName"));
                boolean isNetCDF = "Network Common Data Format".equals(metadata.getProductType());
                boolean isSentinel1 = "Sentinel-1 SAR SAFE Product".equals(metadata.getProductType());
                if (isNetCDF) {
                    // NetCDF requires running gdalinfo twice
                    // Reference: http://www.gdal.org/frmt_netcdf.html
                    JsonObject jsonObject = root.getJsonObject("metadata");
                    jsonObject = jsonObject.getJsonObject("SUBDATASETS");
                    if (jsonObject != null) {
                        JsonValue subDataSet = jsonObject.values().stream()
                                .filter(jv -> jv.toString().contains("NETCDF:"))
                                .findFirst().orElse(null);
                        if (subDataSet != null) {
                            executor = initialize(subDataSet.toString());
                            consumer = new OutputAccumulator();
                            executor.setOutputConsumer(consumer);
                            reader.close();
                            if (executor.execute(true) != 0) {
                                return null;
                            } else {
                                output = consumer.getOutput();
                                reader = new StringReader(output);
                                jsonReader = Json.createReader(reader);
                                root = jsonReader.readObject();
                            }
                        }
                    }
                }
                JsonArray sizeArray = root.getJsonArray("size");
                if (sizeArray != null && sizeArray.size() > 1) {
                    metadata.setWidth(sizeArray.getInt(0));
                    metadata.setHeight(sizeArray.getInt(1));
                }
                JsonObject crsObject;
                if (isNetCDF) {
                    crsObject = root.getJsonObject("metadata").getJsonObject("GEOLOCATION");
                } else if (isSentinel1) {
                    crsObject = root.getJsonObject("gcps").getJsonObject("coordinateSystem");
                } else {
                    crsObject = root.getJsonObject("coordinateSystem");
                }
                if (crsObject != null) {
                    CoordinateReferenceSystem crs = CRS.parseWKT(isNetCDF ? crsObject.getString("SRS") : crsObject.getString("wkt"));
                    if (crs != null && crs.getIdentifiers() != null && crs.getIdentifiers().size() > 0) {
                        ReferenceIdentifier identifier = crs.getIdentifiers().stream()
                                .findFirst().get();
                        metadata.setCrs(identifier.getCodeSpace() + ":" + identifier.getCode());
                    }
                }
                JsonObject extentObject = root.getJsonObject("wgs84Extent");
                if (extentObject != null) {
                    Polygon2D polygon2D = new Polygon2D();
                    JsonArray points = extentObject.getJsonArray("coordinates").getJsonArray(0);
                    for (int i = 0; i < points.size(); i++) {
                        polygon2D.append(points.getJsonArray(i).getJsonNumber(0).doubleValue(),
                                         points.getJsonArray(i).getJsonNumber(1).doubleValue());
                    }
                    metadata.setFootprint(polygon2D.toWKT(8));
                } else if (isSentinel1) {
                    JsonArray gcpList = root.getJsonObject("gcps").getJsonArray("gcpList");
                    if (gcpList != null) {
                        Polygon2D polygon2D = new Polygon2D();
                        JsonObject ul = gcpList.getJsonObject(0);
                        polygon2D.append(ul.getJsonNumber("x").doubleValue(),
                                         ul.getJsonNumber("y").doubleValue());
                        JsonObject point = gcpList.getJsonObject(20);
                        polygon2D.append(point.getJsonNumber("x").doubleValue(),
                                         point.getJsonNumber("y").doubleValue());
                        point = gcpList.getJsonObject(189);
                        polygon2D.append(point.getJsonNumber("x").doubleValue(),
                                         point.getJsonNumber("y").doubleValue());
                        point = gcpList.getJsonObject(209);
                        polygon2D.append(point.getJsonNumber("x").doubleValue(),
                                         point.getJsonNumber("y").doubleValue());
                        polygon2D.append(ul.getJsonNumber("x").doubleValue(),
                                         ul.getJsonNumber("y").doubleValue());
                        metadata.setFootprint(polygon2D.toWKT(8));
                    }
                } else {
                    extentObject = root.getJsonObject("cornerCoordinates");
                    if (extentObject != null) {
                        Polygon2D polygon2D = new Polygon2D();
                        JsonArray ul = extentObject.getJsonArray("upperLeft");
                        if (ul != null) {
                            polygon2D.append(ul.getJsonNumber(0).doubleValue(),
                                             ul.getJsonNumber(1).doubleValue());
                            JsonArray point = extentObject.getJsonArray("lowerLeft");
                            polygon2D.append(point.getJsonNumber(0).doubleValue(),
                                             point.getJsonNumber(1).doubleValue());
                            point = extentObject.getJsonArray("lowerRight");
                            polygon2D.append(point.getJsonNumber(0).doubleValue(),
                                             point.getJsonNumber(1).doubleValue());
                            point = extentObject.getJsonArray("upperRight");
                            polygon2D.append(point.getJsonNumber(0).doubleValue(),
                                             point.getJsonNumber(1).doubleValue());
                            polygon2D.append(ul.getJsonNumber(0).doubleValue(),
                                             ul.getJsonNumber(1).doubleValue());
                            metadata.setFootprint(polygon2D.toWKT(8));
                        }
                    }
                }
                JsonArray bandsArray = root.getJsonArray("bands");
                String type = bandsArray.getJsonObject(0).getString("type");
                if (type != null) {
                    switch (type.toLowerCase()) {
                        case "byte":
                            metadata.setPixelType(PixelType.UINT8);
                            break;
                        case "uint16":
                        case "cint16":
                            metadata.setPixelType(PixelType.UINT16);
                            break;
                        case "int16":
                            metadata.setPixelType(PixelType.INT16);
                            break;
                        case "uint32":
                            metadata.setPixelType(PixelType.UINT32);
                            break;
                        case "int32":
                            metadata.setPixelType(PixelType.INT32);
                            break;
                        case "float32":
                            metadata.setPixelType(PixelType.FLOAT32);
                            break;
                        case "float64":
                            metadata.setPixelType(PixelType.FLOAT64);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        return metadata;
    }

    private Executor initialize(String productPath) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("gdalinfo");
        args.add("-json");
        args.add(productPath);
        try {
            return ProcessExecutor.create(ExecutorType.PROCESS,
                                                       InetAddress.getLocalHost().getHostName(),
                                                       args);
        } catch (UnknownHostException e) {
            throw new IOException(e);
        }
    }
}
