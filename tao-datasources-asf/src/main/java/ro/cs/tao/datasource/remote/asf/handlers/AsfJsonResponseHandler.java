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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import ro.cs.tao.datasource.remote.asf.json.AsfSearchResult;
import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static ro.cs.tao.utils.executors.ByteUnit.MEGABYTE;

/**
 * ASF Search query JSON response handler
 *
 * @author Valentin Netoiu
 */
public class AsfJsonResponseHandler implements JSonResponseHandler<EOProduct> {
    @Override
    public List<EOProduct> readValues(String content, AttributeFilter...filters) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        AsfSearchResult[][] results = mapper.readValue(content, AsfSearchResult[][].class);
        if(results.length > 0){
            return Arrays.stream(results[0]).map(r -> {
                try {
                    EOProduct product = new EOProduct();
                    product.setProductType(r.getPlatform());
                    product.setName(r.getProductName());
                    product.setId(r.getFileId());
                    product.setFormatType(DataFormat.RASTER);
                    product.setSensorType(SensorType.RADAR);
                    product.setPixelType(PixelType.UINT16);
                    product.setWidth(-1);
                    product.setHeight(-1);
                    product.setGeometry(r.getFootprint());
                    product.setLocation(r.getDownloadUrl());
                    product.setProcessingDate(r.getProcessingDate());
                    product.setAcquisitionDate(r.getProcessingDate());
                    String thumbnailUrl = r.getThumbnailUrl();
                    if(!"NA".equalsIgnoreCase(thumbnailUrl) && !"N/A".equalsIgnoreCase(thumbnailUrl)){
                        product.setQuicklookLocation(r.getThumbnailUrl());
                    }
                    product.setApproximateSize(MEGABYTE.value() * (long) Double.parseDouble(r.getSizeMb()));

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

                    return product;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return null;
                }
            }).collect(Collectors.toList());
        }

        return Lists.newArrayList();

    }
}
