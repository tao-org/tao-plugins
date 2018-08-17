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

import org.w3c.dom.*;
import org.xml.sax.SAXException;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.products.sentinels.Sentinel2ProductHelper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

public class Sentinel2MetadataInspector implements MetadataInspector {

    private static DocumentBuilder builder;

    static {
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }
    public Sentinel2MetadataInspector() { }

    @Override
    public Metadata getMetadata(Path productPath) throws IOException {
        if (!Files.exists(productPath)) {
            return null;
        }
        Path productFolderPath = Files.isRegularFile(productPath) ?  productPath.getParent() : productPath;
        Sentinel2ProductHelper helper = Sentinel2ProductHelper.createHelper(productFolderPath.getFileName().toString());
        Metadata metadata = new Metadata();

        String metadataFileName = helper.getMetadataFileName();
        metadata.setEntryPoint(productFolderPath.resolve(metadataFileName).toUri());
        metadata.setPixelType(PixelType.UINT16);
        metadata.setProductType("Sentinel2");
        metadata.setAquisitionDate(LocalDateTime.parse(helper.getSensingDate(), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
        metadata.setSize(FileUtils.folderSize(productFolderPath));
        if (builder != null) {
            try (InputStream inputStream = Files.newInputStream(productFolderPath.resolve(metadataFileName))) {
                Document document = builder.parse(inputStream);
                Element root = document.getDocumentElement();
                String points = getValue("EXT_POS_LIST", root);
                if (points != null) {
                    String[] coords = points.trim().split(" ");
                    Polygon2D polygon2D = new Polygon2D();
                    for (int i = 0; i < coords.length; i += 2) {
                        polygon2D.append(Double.parseDouble(coords[i]),
                                         Double.parseDouble(coords[i + 1]));
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
            } catch (SAXException e) {
                e.printStackTrace();
            }
        }
        return metadata;
    }

    private String getValue(String tagName, Element element) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list != null && list.getLength() > 0) {
            NodeList subList = list.item(0).getChildNodes();
            if (subList != null && subList.getLength() > 0) {
                return subList.item(0).getNodeValue();
            }
        }
        return null;
    }

    private List<String> getValues(String tagName, Element element) {
        List<String> values = null;
        NodeList list = element.getElementsByTagName(tagName);
        if (list != null && list.getLength() > 0) {
            values = new ArrayList<>();
            final int length = list.getLength();
            for (int i = 0; i < length; i++) {
                values.add(list.item(i).getNodeValue());
            }
        }
        return values;
    }

    private List<String> getAttributeValues(String tagName, String attrName, Element element) {
        List<String> values = null;
        NodeList list = element.getElementsByTagName(tagName);
        if (list != null && list.getLength() > 0) {
            values = new ArrayList<>();
            final int length = list.getLength();
            for (int i = 0; i < length; i++) {
                Node node = list.item(i);
                NamedNodeMap attributes = node.getAttributes();
                Node item = attributes.getNamedItem(attrName);
                if (item != null) {
                    values.add(item.getNodeValue());
                }
            }
        }
        return values;
    }
}
