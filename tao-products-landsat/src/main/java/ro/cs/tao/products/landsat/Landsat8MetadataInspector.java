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

package ro.cs.tao.products.landsat;

import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.MetadataInspector;
import ro.cs.tao.utils.FileUtilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Landsat8MetadataInspector implements MetadataInspector {

    public Landsat8MetadataInspector() { }

    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        if (!Files.exists(productPath)) {
            return DecodeStatus.UNABLE;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        try {
            new Landsat8ProductHelper(productFolderPath.getFileName().toString());
            return DecodeStatus.INTENDED;
        } catch (Exception e) {
            return DecodeStatus.UNABLE;
        }
    }

    @Override
    public Metadata getMetadata(Path productPath) throws IOException {
        if (decodeQualification(productPath) == DecodeStatus.UNABLE) {
            return null;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        Landsat8ProductHelper helper = new Landsat8ProductHelper(productFolderPath.getFileName().toString());
        Metadata metadata = new Metadata();
        String metadataFileName = helper.getMetadataFileName();
        metadata.setEntryPoint(metadataFileName);
        metadata.setPixelType(PixelType.UINT16);
        metadata.setProductType("Landsat8");
        metadata.setSensorType(SensorType.OPTICAL);
        metadata.setAquisitionDate(LocalDateTime.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
        metadata.setSize(FileUtilities.folderSize(productFolderPath));
        double[] currentPoint = new double[2];
        double[] firstPoint = null;
        Polygon2D polygon2D = new Polygon2D();
        String utmZone = null;
        for (String line : Files.readAllLines(productPath.resolve(helper.getMetadataFileName()))) {
            if (line.contains("CORNER")) {
                if (line.contains("LAT")) {
                    currentPoint[1] = Double.parseDouble(line.substring(line.indexOf("=") + 1).trim());
                } else if (line.contains("LON")) {
                    currentPoint[0] = Double.parseDouble(line.substring(line.indexOf("=") + 1).trim());
                    if (firstPoint == null) {
                        firstPoint = new double[] { currentPoint[0], currentPoint[1] };
                    }
                    polygon2D.append(currentPoint[0], currentPoint[1]);
                }
            }
            if (line.contains("PANCHROMATIC_LINES")) {
                metadata.setHeight(Integer.parseInt(line.substring(line.indexOf("=") + 1).trim()));
            }
            if (line.contains("PANCHROMATIC_SAMPLES")) {
                metadata.setWidth(Integer.parseInt(line.substring(line.indexOf("=") + 1).trim()));
            }
            if (line.contains("UTM_ZONE")) {
                utmZone = line.substring(line.indexOf("=") + 1).trim();
            }
        }
        if (firstPoint != null) {
            polygon2D.append(firstPoint[0], firstPoint[1]);
            metadata.setFootprint(polygon2D.toWKT(5));
            if (utmZone != null) {
                metadata.setCrs("EPSG:32" + (firstPoint[1] > 0 ? "6" : "7") + utmZone);
            }
        }
        return metadata;
    }
}
