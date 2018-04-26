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

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
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
        Executor executor = initialize(productPath);
        OutputAccumulator consumer = new OutputAccumulator();
        executor.setOutputConsumer(consumer);
        StringReader reader = null;
        Metadata metadata = null;
        try {
            if (executor.execute(false) == 0) {
                String output = consumer.getOutput();
                reader = new StringReader(output);
                JsonReader jsonReader = Json.createReader(reader);
                JsonObject root = jsonReader.readObject();
                metadata = new Metadata();
                metadata.setEntryPoint(productPath.toUri());
                metadata.setProductType(root.getString("driverLongName"));
                JsonArray sizeArray = root.getJsonArray("size");
                if (sizeArray != null) {
                    metadata.setWidth(sizeArray.getInt(0));
                    metadata.setHeight(sizeArray.getInt(1));
                }
                JsonObject crsObject = root.getJsonObject("coordinateSystem");
                if (crsObject != null) {
                    CoordinateReferenceSystem crs = CRS.parseWKT(crsObject.getString("wkt"));
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
                        polygon2D.append(points.getJsonArray(0).getJsonNumber(0).doubleValue(),
                                         points.getJsonArray(0).getJsonNumber(1).doubleValue());
                    }
                    metadata.setFootprint(polygon2D.toWKT());
                }
                JsonArray bandsArray = root.getJsonArray("bands");
                String type = bandsArray.getJsonObject(0).getString("type");
                if (type != null) {
                    switch (type.toLowerCase()) {
                        case "byte":
                            metadata.setPixelType(PixelType.UINT8);
                            break;
                        case "uint16":
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

    private Executor initialize(Path productPath) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("gdalinfo");
        args.add("-json");
        args.add(productPath.toAbsolutePath().toString());
        try {
            return ProcessExecutor.create(ExecutorType.PROCESS,
                                                       InetAddress.getLocalHost().getHostName(),
                                                       args);
        } catch (UnknownHostException e) {
            throw new IOException(e);
        }
    }
}
