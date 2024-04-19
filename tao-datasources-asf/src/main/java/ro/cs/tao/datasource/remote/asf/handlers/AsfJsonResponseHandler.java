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
package ro.cs.tao.datasource.remote.asf.handlers;

import ro.cs.tao.datasource.remote.asf.json.AsfSearchResult;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.serialization.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.nonNull;
import static ro.cs.tao.utils.executors.MemoryUnit.MB;

/**
 * ASF Search query JSON response handler
 *
 * @author Valentin Netoiu
 */
public class AsfJsonResponseHandler implements JSonResponseHandler<EOProduct> {
    static final Pattern S1Pattern =
            Pattern.compile("(S1[A-B])_(SM|IW|EW|WV)_(SLC|GRD|RAW|OCN)([FHM_])_([0-2])([AS])(SH|SV|DH|DV)_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(\\d{6})_([0-9A-F]{6})_([0-9A-F]{4})(?:.SAFE)?");

    private final Predicate<EOProduct> filter;

    public AsfJsonResponseHandler(Predicate<EOProduct> filter) {
        this.filter = filter;
    }

    @Override
    public List<EOProduct> readValues(String content, AttributeFilter...filters) throws IOException {
        AsfSearchResult[][] results = JsonMapper.instance().readValue(content, AsfSearchResult[][].class);
        final List<EOProduct> retVal = new ArrayList<>();
        if (results.length > 0) {
            for (AsfSearchResult r : results[0]) {
                try {
                    EOProduct product = new EOProduct();
                    //product.setSatelliteName(r.getPlatform());
                    product.setGeometry(r.getFootprint());
                    if (this.filter != null && this.filter.test(product)) {
                        continue;
                    }
                    product.setName(r.getProductName());
                    product.setId(r.getFileId());
                    product.setFormatType(DataFormat.RASTER);
                    product.setSensorType(SensorType.RADAR);
                    product.setPixelType(PixelType.UINT16);
                    product.setWidth(-1);
                    product.setHeight(-1);
                    product.setLocation(r.getDownloadUrl());
                    product.setProcessingDate(r.getProcessingDate());
                    if (r.getSceneDate() != null) {
                        product.setAcquisitionDate(r.getSceneDate());
                    } else {
                        product.setAcquisitionDate(r.getStartTime());
                    }
                    String thumbnailUrl = r.getThumbnailUrl();
                    if(!"NA".equalsIgnoreCase(thumbnailUrl) && !"N/A".equalsIgnoreCase(thumbnailUrl)){
                        product.setQuicklookLocation(r.getThumbnailUrl());
                    }
                    product.setApproximateSize(MB.value() * (long) Double.parseDouble(r.getSizeMb()));
                    //productType read from sensor property
                    String sensor = r.getSensor().trim();
                    if(sensor.contains(" ")){
                        //get the sensor and instrument name
                        String[] sensorAttributes = sensor.split(" ");
                        String sensorName = sensorAttributes[0].toLowerCase();
                        String instrument = sensorAttributes[1];
                        //overwrite the producttype
                        product.setProductType(sensorName);
                        //add sensor attribute
                        product.addAttribute("instrumentshortname", instrument);
                    } else {
                        if (S1Pattern.matcher(r.getProductName()).matches()) {
                            product.setProductType("Sentinel1");
                        }
                        product.addAttribute("instrumentshortname", sensor);
                    }
                    //add some attributes
                    if(nonNull(r.getFileName())){
                        product.addAttribute("filename", r.getFileName());
                    }
                    if(nonNull(r.getFlightDirection())){
                        product.addAttribute("flightdirection", r.getFlightDirection());
                    }
                    if(nonNull(r.getPolarisation())){
                        product.addAttribute("polarisation", r.getPolarisation());
                    }
                    if(nonNull(r.getProcessingLevel())){
                        product.addAttribute("processinglevel", r.getProcessingLevel());
                    }
                    if(nonNull(r.getProcessingType())){
                        product.addAttribute("processingtype", r.getProcessingType());
                    }
                    Object value = r.getAdditionalProperties().get("relativeOrbit");
                    if (value != null) {
                        product.addAttribute("relativeOrbit", value.toString());
                    } else if (product.getName().startsWith("S1")) {
                        product.addAttribute("relativeOrbit", getRelativeOrbit(product.getName()));
                    }
                    retVal.add(product);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return retVal;
    }

    private String getRelativeOrbit(String productName) {
        final String value;
        final Matcher matcher = S1Pattern.matcher(productName);
        if (matcher.matches()) {
            int absOrbit = Integer.parseInt(matcher.group(10));
            value = String.format("%03d",
                    matcher.group(1).endsWith("A") ?
                            ((absOrbit - 73) % 175) + 1 :
                            ((absOrbit - 27) % 175) + 1);
        } else {
            value = null;
        }
        return value;
    }
}
