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

package ro.cs.tao.products.sentinels;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.metadata.DecodeStatus;
import ro.cs.tao.eodata.metadata.XmlMetadataInspector;
import ro.cs.tao.utils.FileUtils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        metadata.setProductType("Sentinel2");
        metadata.setAquisitionDate(LocalDateTime.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
        metadata.setSize(FileUtils.folderSize(productFolderPath));
        try (InputStream inputStream = Files.newInputStream(productFolderPath.resolve(metadataFileName))) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(inputStream);
            Element root = document.getDocumentElement();
            String points = getValue("EXT_POS_LIST", root);
            if (points != null) {
                String[] coords = points.trim().split(" ");
                Polygon2D polygon2D = new Polygon2D();
                for (String coord : coords) {
                    polygon2D.append(Double.parseDouble(coord.substring(0, coord.indexOf(','))),
                                     Double.parseDouble(coord.substring(coord.indexOf(',') + 1)));
                }
                metadata.setFootprint(polygon2D.toWKT(8));
            }
            metadata.setCrs("EPSG:4326");
            if (Sentinel2ProductHelper.PSD_14.equals(helper.getVersion())) {
                metadata.setWidth(10980);
                metadata.setHeight(10980);
            } else {
                List<String> granuleList = getAttributeValues("Granules", "granuleIdentifier", root);
                if (granuleList == null) {
                    granuleList = getAttributeValues("Granule", "granuleIdentifier", root);
                }
                if (granuleList != null) {
                    for (String gId : granuleList) {
                        String granuleMetadataFileName = helper.getGranuleMetadataFileName(gId);
                        Path granuleMetadataFile = productFolderPath.resolve("GRANULE")
                                                                    .resolve(gId)
                                                                    .resolve(granuleMetadataFileName);
                        try (InputStream gis = Files.newInputStream(granuleMetadataFile)) {
                            Document gDoc = builder.parse(gis);
                            Element gRoot = gDoc.getDocumentElement();
                            List<String> ulx = getValues("ULX", gRoot);
                            List<String> uly = getValues("ULY", gRoot);
                            if (ulx != null && uly != null) {
                                LongStream ulxStm = ulx.stream().mapToLong(Long::parseLong);
                                LongStream ulyStm = uly.stream().mapToLong(Long::parseLong);
                                metadata.setWidth((int) ((ulxStm.max().orElse(0) - ulxStm.min().orElse(0)) / 10) + 10980);
                                metadata.setHeight((int) ((ulyStm.max().orElse(0) - ulyStm.min().orElse(0)) / 10) + 10980);
                            }
                        }
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }
        return metadata;
    }
}
