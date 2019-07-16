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

package ro.cs.tao.products.sentinels;

import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.OrbitDirection;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.XmlMetadataInspector;
import ro.cs.tao.utils.FileUtilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Simple metadata inspector for Sentinel-1 products
 */
public class Sentinel1MetadataInspector extends XmlMetadataInspector {

    private static final String XPATH_ORBIT_DIRECTION = "/xfdu:XFDU/metadataSection/metadataObject[@ID='measurementOrbitReference']/metadataWrap/xmlData/safe:orbitReference/safe:extension/s1:orbitProperties/s1:pass/text()";
    private static final String XPATH_COORDINATES = "/xfdu:XFDU/metadataSection/metadataObject[@ID='measurementFrameSet']/metadataWrap/xmlData/safe:frameSet/safe:frame/safe:footPrint/gml:coordinates";
    private static final String XPATH_CHECKSUM = "/xfdu:XFDU/dataObjectSection/dataObject/byteStream/fileLocation[starts-with(@href, './measurement')]/checksum/text()";
    private static final String XPATH_PIXEL_TYPE = "/product/imageAnnotation/imageInformation/outputPixels/text()";
    private static final String XPATH_SAMPLES = "/product/imageAnnotation/imageInformation/numberOfSamples/text()";
    private static final String XPATH_LINES = "/product/imageAnnotation/imageInformation/numberOfLines/text()";
    private static final String XPATH_BURST_LIST = "/product/swathTiming/burstList/@count";

    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        if (!Files.exists(productPath)) {
            return DecodeStatus.UNABLE;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        try {
            new Sentinel1ProductHelper(productFolderPath.getFileName().toString());
            return DecodeStatus.INTENDED;
        } catch (Exception e) {
            return DecodeStatus.UNABLE;
        }
    }

    @Override
    public Metadata getMetadata(Path productPath) throws IOException {
        if (!Files.exists(productPath)) {
            return null;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        Sentinel1ProductHelper helper = new Sentinel1ProductHelper(productFolderPath.getFileName().toString());
        Metadata metadata = new Metadata();

        String metadataFileName = helper.getMetadataFileName();
        metadata.setEntryPoint(metadataFileName);
        metadata.setProductType("Sentinel1");
        metadata.setSensorType(SensorType.RADAR);
        metadata.setAquisitionDate(LocalDateTime.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
        metadata.setSize(FileUtilities.folderSize(productFolderPath));
        try {
            readDocument(productFolderPath.resolve(metadataFileName));
        } catch (Exception e) {
            logger.warning(String.format("Cannot read metadata %s. Reason: %s", metadataFileName, e.getMessage()));
            throw new IOException(e);
        }
        metadata.setOrbitDirection(OrbitDirection.valueOf(getValue(XPATH_ORBIT_DIRECTION)));
        String points = getValue(XPATH_COORDINATES);
        String[] coords = points.trim().split(" ");
        Polygon2D polygon2D = new Polygon2D();
        for (String coord : coords) {
            polygon2D.append(Double.parseDouble(coord.substring(coord.indexOf(',') + 1)),
                             Double.parseDouble(coord.substring(0, coord.indexOf(','))));
        }
        polygon2D.append(Double.parseDouble(coords[0].substring(coords[0].indexOf(',') + 1)),
                         Double.parseDouble(coords[0].substring(0, coords[0].indexOf(','))));
        metadata.setFootprint(polygon2D.toWKT(8));
        List<String> checkSums = getValues(XPATH_CHECKSUM);
        for (String checkSum : checkSums) {
            metadata.addControlSum(checkSum);
        }
        metadata.setCrs("EPSG:4326");
        List<Path> annotations = FileUtilities.listFilesWithExtension(productFolderPath.resolve("annotation"), ".xml");
        if (annotations != null) {
            for (Path annotation : annotations) {
                try {
                    readDocument(annotation);
                    String pixelType = getValue(XPATH_PIXEL_TYPE);
                    metadata.setPixelType("16 bit Signed Integer".equals(pixelType) ? PixelType.INT16 : PixelType.UINT16);
                    metadata.setWidth(Math.max(metadata.getWidth(), Integer.parseInt(getValue(XPATH_SAMPLES))));
                    metadata.setHeight(Math.max(metadata.getHeight(), Integer.parseInt(getValue(XPATH_LINES))));
                    String value = getValue(XPATH_BURST_LIST);
                    if (value != null) {
                        metadata.addAttribute(annotation.getFileName().toString().substring(4, 7), value);
                    }
                } catch (Exception e) {
                    logger.warning(String.format("Cannot read annotation metadata %s. Reason: %s", annotation, e.getMessage()));
                }
            }
        }
        return metadata;
    }

    private class BooleanWrapper {
        private boolean value = false;
    }
}
