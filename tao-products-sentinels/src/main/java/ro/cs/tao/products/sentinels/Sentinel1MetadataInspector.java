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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple metadata inspector for Sentinel-1 products
 */
public class Sentinel1MetadataInspector extends XmlMetadataInspector {

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
        metadata.setAquisitionDate(LocalDateTime.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
        metadata.setSize(FileUtils.folderSize(productFolderPath));
        try (InputStream inputStream = Files.newInputStream(productFolderPath.resolve(metadataFileName))) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(inputStream);
            Element root = document.getDocumentElement();
            String points = getValue("coordinates", root);
            if (points != null) {
                String[] coords = points.trim().split(" ");
                Polygon2D polygon2D = new Polygon2D();
                for (String coord : coords) {
                    polygon2D.append(Double.parseDouble(coord.substring(0, coord.indexOf(','))),
                                     Double.parseDouble(coord.substring(coord.indexOf(',') + 1)));
                }
                polygon2D.append(Double.parseDouble(coords[0].substring(0, coords[0].indexOf(','))),
                                 Double.parseDouble(coords[0].substring(coords[0].indexOf(',') + 1)));
                metadata.setFootprint(polygon2D.toWKT(6));
            }
            metadata.setCrs("EPSG:4326");
            File[] annotations = FileUtils.listFilesWithExtension(productFolderPath.resolve("annotation").toFile(), ".xml");
            if (annotations != null) {
                for (File annotation : annotations) {
                    try (InputStream gis = Files.newInputStream(annotation.toPath())) {
                        Document gDoc = builder.parse(gis);
                        Element gRoot = gDoc.getDocumentElement();
                        if (metadata.getPixelType() == null) {
                            metadata.setPixelType("16 bit Signed Integer".equals(getValue("outputPixels", gRoot)) ?
                                                    PixelType.INT16 : PixelType.UINT16);
                        }
                        metadata.setWidth(Math.max(metadata.getWidth(), Integer.parseInt(getValue("numberOfSamples", gRoot))));
                        metadata.setHeight(Math.max(metadata.getHeight(), Integer.parseInt(getValue("numberOfLines", gRoot))));
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }
        return metadata;
    }
}