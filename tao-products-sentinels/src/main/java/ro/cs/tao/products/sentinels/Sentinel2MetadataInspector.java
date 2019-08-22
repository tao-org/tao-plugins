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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

/**
 * Simple metadata inspector for Sentinel-2 products
 */
public class Sentinel2MetadataInspector extends XmlMetadataInspector {

    public Sentinel2MetadataInspector() { super(); }

    @Override
    public DecodeStatus decodeQualification(Path productPath) {
        if (!Files.exists(productPath)) {
            return DecodeStatus.UNABLE;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        try {
            Sentinel2ProductHelper.createHelper(productFolderPath.getFileName().toString());
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
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(productFolderPath.getFileName().toString());
        Metadata metadata = new Metadata();

        String metadataFileName = helper.getMetadataFileName();
        metadata.setEntryPoint(metadataFileName);
        metadata.setPixelType(PixelType.UINT16);
        metadata.setSensorType(SensorType.OPTICAL);
        metadata.setProductType("Sentinel2");
        metadata.setAquisitionDate(LocalDateTime.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
        metadata.setSize(FileUtilities.folderSize(productFolderPath));
        try {
            readDocument(productFolderPath.resolve(metadataFileName));
        } catch (Exception e) {
            logger.warning(String.format("Cannot read metadata %s. Reason: %s", metadataFileName, e.getMessage()));
            throw new IOException(e);
        }
        String points = getValue("/Level-1C_User_Product/Geometric_Info/Product_Footprint/Product_Footprint/Global_Footprint/EXT_POS_LIST/text()");
        if (points != null) {
            String[] coords = points.trim().split(" ");
            Polygon2D polygon2D = new Polygon2D();
            for (int i = 0; i < coords.length; i += 2) {
                polygon2D.append(Double.parseDouble(coords[i + 1]),
                                 Double.parseDouble(coords[i]));
            }
            metadata.setFootprint(polygon2D.toWKT(8));
        }
        metadata.setCrs("EPSG:4326");
        metadata.setOrbitDirection(OrbitDirection.valueOf(getValue("/Level-1C_User_Product/General_Info/Product_Info/Datatake/SENSING_ORBIT_DIRECTION/text()")));
        if (Sentinel2ProductHelper.PSD_14.equals(helper.getVersion())) {
            metadata.setWidth(10980);
            metadata.setHeight(10980);
        } else {
            List<String> granuleList = getValues("/Level-1C_User_Product/Product_Organisation/Granule_List/Granules/@granuleIdentifier");
            if (granuleList == null) {
                granuleList = getValues("/Level-1C_User_Product/Product_Organisation/Granule_List/Granule/@granuleIdentifier");
            }
            List<String> ulx = new ArrayList<>();
            List<String> uly = new ArrayList<>();
            for (String gId : granuleList) {
                String granuleMetadataFileName = helper.getGranuleMetadataFileName(gId);
                Path granuleMetadataFile = productFolderPath.resolve("GRANULE")
                        .resolve(gId)
                        .resolve(granuleMetadataFileName);
                try {
                    readDocument(granuleMetadataFile);
                    List<String> ulxg = getValues("/Level-1C_Tile_ID/n1:Geometric_Info/Tile_Geocoding/Geoposition/ULX/text()");
                    if (ulxg != null) {
                        ulx.addAll(ulxg);
                    }
                    List<String> ulyg = getValues("/Level-1C_Tile_ID/n1:Geometric_Info/Tile_Geocoding/Geoposition/ULY/text()");
                    if (ulyg != null) {
                        uly.addAll(ulyg);
                    }
                } catch (Exception e) {
                    logger.warning(String.format("Cannot read granule metadata %s. Reason: %s", granuleMetadataFile, e.getMessage()));
                }
            }
            LongStream ulxStm = ulx.stream().mapToLong(Long::parseLong);
            LongStream ulyStm = uly.stream().mapToLong(Long::parseLong);
            metadata.setWidth((int) ((ulxStm.max().orElse(0) - ulxStm.min().orElse(0)) / 10) + 10980);
            metadata.setHeight((int) ((ulyStm.max().orElse(0) - ulyStm.min().orElse(0)) / 10) + 10980);
        }
        return metadata;
    }
}
