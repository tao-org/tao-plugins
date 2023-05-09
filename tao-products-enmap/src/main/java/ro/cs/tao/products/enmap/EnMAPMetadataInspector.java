/*
 * Copyright (C) 2022 CS GROUP ROMANIA
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

package ro.cs.tao.products.enmap;

import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.OrbitDirection;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.XmlMetadataInspector;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.FileProcessFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Simple metadata inspector for EnMAP products
 */
public class EnMAPMetadataInspector extends XmlMetadataInspector {

    public EnMAPMetadataInspector() {
        super();
    }

    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        try {
            if (Files.exists(productPath)) {
                final Path productFolderPath = Files.isRegularFile(productPath) ? productPath.getParent() : productPath;
                final EnMAPProductHelper helper = EnMAPProductHelper.createHelper(productFolderPath);
                return Files.exists(productFolderPath.resolve(helper.getMetadataFileName())) ? DecodeStatus.INTENDED : DecodeStatus.UNABLE;
            }
            return DecodeStatus.UNABLE;
        } catch (Exception e) {
            return DecodeStatus.UNABLE;
        }
    }

    @Override
    public Metadata getMetadata(Path productPath) throws IOException {
        if (decodeQualification(productPath) == DecodeStatus.UNABLE) {
            return null;
        }
        final Path productFolderPath = Files.isRegularFile(productPath) ? productPath.getParent() : productPath;
        final EnMAPProductHelper helper = EnMAPProductHelper.createHelper(productFolderPath);
        final Metadata metadata = new Metadata();

        final String metadataFileName = helper.getMetadataFileName();
        metadata.setEntryPoint(metadataFileName);
        metadata.setPixelType(PixelType.UINT16);
        metadata.setSensorType(SensorType.OPTICAL);
        metadata.setProductType("EnMAP");
        String sensingDate = helper.getSensingDate();
        if (sensingDate != null) {
            metadata.setAquisitionDate(LocalDateTime.parse(sensingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")));
        }
        metadata.setSize(FileUtilities.folderSize(productFolderPath));
        try {
            readDocument(productFolderPath.resolve(metadataFileName));
        } catch (Exception e) {
            logger.warning(String.format("Cannot read metadata %s. Reason: %s", metadataFileName, e.getMessage()));
            throw new IOException(e);
        }
        metadata.setProductId(getValue("/level_X/metadata/name/text()"));
        if (metadata.getAquisitionDate() == null) {
            sensingDate = getValue("/level_X/base/temporalCoverage/startTime/text()");
            if (sensingDate != null) {
                metadata.setAquisitionDate(LocalDateTime.parse(sensingDate, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")));
            }
        }
        final List<String> latitudeCoordinates = getValues("/level_X/base/spatialCoverage/boundingPolygon/point/latitude/text()");
        final List<String> longitudeCoordinates = getValues("/level_X/base/spatialCoverage/boundingPolygon/point/longitude/text()");
        final Polygon2D footprint = new Polygon2D();
        for (int i = 0; i < Math.min(latitudeCoordinates.size(), longitudeCoordinates.size()); i++) {
            footprint.append(Double.parseDouble(longitudeCoordinates.get(i)),
                    Double.parseDouble(latitudeCoordinates.get(i)));
        }
        footprint.append(Double.parseDouble(longitudeCoordinates.get(0)),
                Double.parseDouble(latitudeCoordinates.get(0)));
        metadata.setCrs("EPSG:4326");
        metadata.setFootprint(footprint.toWKT());
        metadata.setOrbitDirection(OrbitDirection.valueOf(getValue("/level_X/specific/orbitDirection/text()")));
        metadata.setWidth(Integer.parseInt(getValue("/level_X/specific/widthOfScene/text()")));
        metadata.setHeight(Integer.parseInt(getValue("/level_X/specific/heightOfScene/text()")));
        return metadata;
    }

    @Override
    public void setFileProcessFactory(FileProcessFactory factory) {
        //NOOP - only intended for local access
    }
}
