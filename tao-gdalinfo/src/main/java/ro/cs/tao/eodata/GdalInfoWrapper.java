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

package ro.cs.tao.eodata;

import org.geotools.referencing.CRS;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import ro.cs.tao.configuration.ConfigurationManager;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.products.landsat.Landsat8MetadataInspector;
import ro.cs.tao.products.sentinels.Sentinel1MetadataInspector;
import ro.cs.tao.products.sentinels.Sentinel2MetadataInspector;
import ro.cs.tao.utils.DockerHelper;
import ro.cs.tao.utils.executors.Executor;
import ro.cs.tao.utils.executors.ExecutorType;
import ro.cs.tao.utils.executors.OutputAccumulator;
import ro.cs.tao.utils.executors.ProcessExecutor;

import javax.json.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GdalInfoWrapper implements MetadataInspector {
    private static final String gdalDockerImage;
    private static final boolean useDocker;
    private static final String[] gdalOnPathCmd;
    private static final String[] gdalOnDocker;

    static {
        ConfigurationManager configurationManager = ConfigurationManager.getInstance();
        gdalDockerImage = configurationManager.getValue("docker.gdal.image", "geodata/gdal");
        useDocker = Boolean.parseBoolean(configurationManager.getValue("plugins.use.docker", "false"));
        gdalOnPathCmd = new String[] {
                "gdalinfo", "-stats", "-json", "$FULL_PATH"
        };
        gdalOnDocker = new String[] {
                "docker", "run", "-t", "--rm", "-v", "$FOLDER:/mnt", gdalDockerImage, "gdalinfo", "-stats","-json", "/mnt/$FILE"
        };
    }

    public GdalInfoWrapper() { }

    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        return productPath == null || !Files.exists(productPath) ? DecodeStatus.UNABLE : DecodeStatus.SUITABLE;
    }

    @Override
    public Metadata getMetadata(Path productPath) throws IOException {
        if (productPath == null) {
            return null;
        }
        Executor executor;
        try {
            return new Sentinel2MetadataInspector().getMetadata(productPath);
        } catch (Exception notS2) {
            try {
                return new Sentinel1MetadataInspector().getMetadata(productPath);
            } catch (Exception notS1) {
                try {
                    return new Landsat8MetadataInspector().getMetadata(productPath);
                } catch (Exception ignored) {}
            }
        }
        if (Files.isDirectory(productPath)) {
            // gdalinfo would work only on files
            return null;
        }
        executor = initialize(productPath.toAbsolutePath(),
                              useDocker && DockerHelper.isDockerFound() ? gdalOnDocker : gdalOnPathCmd);
        OutputAccumulator consumer = new OutputAccumulator();
        executor.setOutputConsumer(consumer);
        StringReader reader = null;
        Metadata metadata;
        try {
            if (executor.execute(true) == 0) {
                String output = consumer.getOutput();
                reader = new StringReader(output);
                JsonReader jsonReader = Json.createReader(reader);
                JsonObject root = jsonReader.readObject();
                metadata = new Metadata();
                metadata.setEntryPoint(productPath.getFileName().toString());
                metadata.setSize(Files.size(productPath));
                metadata.setProductType(root.getString("driverLongName"));
                boolean isNetCDF = "Network Common Data Format".equals(metadata.getProductType());
                //boolean isSentinel1 = "Sentinel-1 SAR SAFE Product".equals(metadata.getProductType());
                //boolean isSentinel2 = "Sentinel 2".equals(metadata.getProductType());
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
                            executor = initialize(Paths.get(subDataSet.toString()), gdalOnDocker);
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
                } /*else if (isSentinel1) {
                    crsObject = root.getJsonObject("gcps").getJsonObject("coordinateSystem");
                } else if (isSentinel2) {
                    crsObject = root.getJsonObject("metadata").getJsonObject("SUBDATASETS");
                } */else {
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
                } /*else if (isSentinel1) {
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
                } else if (isSentinel2) {
                    String wkt = root.getJsonObject("metadata").getJsonObject("").getString("FOOTPRINT");
                    metadata.setFootprint(wkt);
                }*/ else {
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
                JsonObject jsonObject = bandsArray.size() > 0 ? bandsArray.getJsonObject(0) : null;
                if (jsonObject != null) {
                    String type = jsonObject.getString("type");
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
                    JsonNumber value = jsonObject.getJsonNumber("minimum");
                    if (value != null) {
                        metadata.addStatistic("min", value.doubleValue());
                    }
                    value = jsonObject.getJsonNumber("maximum");
                    if (value != null) {
                        metadata.addStatistic("max", value.doubleValue());
                    }
                    value = jsonObject.getJsonNumber("mean");
                    if (value != null) {
                        metadata.addStatistic("mean", value.doubleValue());
                    }
                    value = jsonObject.getJsonNumber("stdDev");
                    if (value != null) {
                        metadata.addStatistic("stdDev", value.doubleValue());
                    }
                }
            } else {
                throw new IOException(consumer.getOutput());
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

    private Executor initialize(Path path, String[] args) throws IOException {
        List<String> arguments = new ArrayList<>();
        for (String arg : args) {
            arguments.add(arg.replace("$FULL_PATH", path.toString())
                              .replace("$FOLDER", path.getParent().toString())
                              .replace("$FILE", path.getFileName().toString()));
        }
        try {
            return ProcessExecutor.create(ExecutorType.PROCESS,
                                                       InetAddress.getLocalHost().getHostName(),
                                          arguments);
        } catch (UnknownHostException e) {
            throw new IOException(e);
        }
    }
}
