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
package ro.cs.tao.datasource.remote.das.json;

import ro.cs.tao.datasource.remote.das.DASDataSource;
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

/**
 * @author Cosmin Cara
 */
public class DASJsonResponseHandler implements JSonResponseHandler<EOProduct> {
    static final Pattern S1Pattern =
            Pattern.compile("(S1[A-B])_(SM|IW|EW|WV)_(SLC|GRD|RAW|OCN)([FHM_])_([0-2])([AS])(SH|SV|DH|DV)_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(\\d{6})_([0-9A-F]{6})_([0-9A-F]{4})(?:.SAFE)?");
    private final DASDataSource dataSource;
    private final Predicate<EOProduct> filter;

    public DASJsonResponseHandler(DASDataSource dataSource, Predicate<EOProduct> filter) {
        this.dataSource = dataSource;
        this.filter = filter;
    }

    @Override
    public List<EOProduct> readValues(String content, AttributeFilter...filters) throws IOException {
        Results results = JsonMapper.instance().readValue(content, Results.class);
        final List<EOProduct> retVal = new ArrayList<>();
        final List<Result> resultList = results.getResults();
        if (resultList != null) {
            for (Result r : resultList) {
                try {
                    EOProduct product = new EOProduct();
                    final String footprint = r.getFootprint();
                    if (footprint != null) {
                        product.setGeometry(footprint.replace("geography'SRID=4326;", "").replace("'", ""));
                        if (this.filter != null && this.filter.test(product)) {
                            continue;
                        }
                    }
                    final boolean isAux = r.getName().contains("AUX");
                    product.setName(r.getName());
                    product.setId(r.getId());
                    product.setFormatType(isAux ? DataFormat.OTHER : DataFormat.RASTER);
                    Object value = getAttributeValue(r, "platformShortName");
                    if (value != null) {
                        String platform = value.toString();
                        platform = platform.substring(0, 1).toUpperCase() + platform.substring(1).toLowerCase();
                        product.setSensorType(platform.startsWith("Sentinel1") || isAux
                                              ? SensorType.RADAR
                                              : platform.equalsIgnoreCase("sentinel5p")
                                                ? SensorType.ATMOSPHERIC
                                                : SensorType.OPTICAL);
                        product.setSatelliteName(platform);
                    }
                    product.setPixelType(PixelType.UINT16);
                    product.setWidth(-1);
                    product.setHeight(-1);
                    value = getAttributeValue(r, "productType");
                    if (value != null) {
                        product.setProductType(value.toString());
                    }
                    product.setLocation((r.getName().contains("AUX")
                                            ? dataSource.getConnectionString("Sentinel1-orbit-files")
                                            : dataSource.getConnectionString("Download")) + "(" + r.getId() + ")/$value");
                    product.setAcquisitionDate(r.getContentDate().getStart());
                    final List<Attribute> attributes = r.getAttributes();
                    final Attribute orbitNumber = attributes.stream().filter(a -> a.getName().equals("relativeOrbitNumber")).findFirst().orElse(null);
                    product.addAttribute("relativeOrbit", orbitNumber != null
                                                          ? String.valueOf(orbitNumber.getValue())
                                                          : getRelativeOrbit(product.getName()));
                    product.addAttribute("s3path", r.getS3Path());
                    attributes.forEach(a -> product.addAttribute(a.getName(), String.valueOf(a.getValue())));
                    final List<Asset> assets = r.getAssets();
                    if (assets != null) {
                        final Asset asset = assets.stream().filter(a -> "quicklook".equalsIgnoreCase(a.getType())).findFirst().orElse(null);
                        product.setQuicklookLocation(asset != null ? asset.getDownloadLink() : null);
                    }
                    retVal.add(product);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return retVal;
    }

    private Object getAttributeValue(Result result, String name) {
        Attribute attribute = result.getAttributes().stream().filter(a -> a.getName().equals(name)).findFirst().orElse(null);
        return attribute != null ? attribute.getValue() : null;
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
