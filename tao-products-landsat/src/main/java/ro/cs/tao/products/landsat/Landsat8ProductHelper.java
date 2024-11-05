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

import ro.cs.tao.eodata.util.BaseProductHelper;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Cosmin Cara
 */
public class Landsat8ProductHelper extends BaseProductHelper {
    private static final Pattern preCollectionNamePattern = Pattern.compile("L\\w[1-8](\\d{3})(\\d{3})(\\d{4})(\\d{3})\\w{3}\\d{2}");
    private static final Pattern collectionNamePattern = Pattern.compile("L\\w\\d{2}_L[1-2]\\w{2}_(\\d{3})(\\d{3})_(\\d{4})(\\d{2})(\\d{2})_\\d{8}_\\d{2}_(\\w{2})");
    private static final String PRE_COLLECTION = "pre-collection";
    private static final String COLLECTION_1 = "collection-1";
    private static final String COLLECTION_2 = "collection-2";
    private int format;
    private String[] nameTokens;
    private String row;
    private String path;
    private LandsatProduct productType;

    public Landsat8ProductHelper() {
        super();
    }

    public Landsat8ProductHelper(String name) {
        super(name);
        switch (this.format) {
            case 1:
                this.version = PRE_COLLECTION;
                this.nameTokens = getTokens(preCollectionNamePattern, name, null);
                break;
            case 2:
                this.version = COLLECTION_1;
                this.nameTokens = getTokens(collectionNamePattern, name, null);
                break;
            case 3:
                this.version = COLLECTION_2;
                this.nameTokens = getTokens(collectionNamePattern, name, null);
                break;
        }
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Landsat8ProductHelper duplicate() {
        return new Landsat8ProductHelper(getName());
    }

    @Override
    public Pattern getTilePattern() { return Pattern.compile("(?:.+)(\\d{6})(?:.+)"); }

    @Override
    public String getVersion() {
        return this.version;
    }

    public LandsatProduct getProductType() {
        if (this.productType == null) {
            this.productType = Enum.valueOf(LandsatProduct.class, nameTokens[5]);
        }
        return this.productType;
    }

    public String getRow() {
        if (this.row == null && this.nameTokens != null) {
            this.row = nameTokens[1];
        }
        return this.row;
    }

    void setRow(String row) {
        this.row = row;
    }

    public String getPath() {
        if (this.path == null && this.nameTokens != null) {
            this.path = nameTokens[0];
        }
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String getMetadataFileName() { return this.name + "_MTL.txt"; }

    @Override
    public String getProductRelativePath() {
        StringBuilder buffer = new StringBuilder();
        if (this.format > 1) {
            buffer.append("c").append(this.format - 1).append(URL_SEPARATOR);
        }
        buffer.append("L8").append(URL_SEPARATOR);
        buffer.append(getPath()).append(URL_SEPARATOR);
        buffer.append(getRow()).append(URL_SEPARATOR);
        buffer.append(this.name).append(URL_SEPARATOR);
        return buffer.toString();
    }

    @Override
    public String getSensingDate() {
        LocalDate localDate;
        Matcher matcher;
        if (this.format == 1) {
            matcher = preCollectionNamePattern.matcher(name);
            //noinspection ResultOfMethodCallIgnored
            matcher.matches();
            localDate = Year.of(Integer.parseInt(matcher.group(3))).atDay(Integer.parseInt(matcher.group(4)));
        } else {
            matcher = collectionNamePattern.matcher(name);
            //noinspection ResultOfMethodCallIgnored
            matcher.matches();
            localDate = LocalDate.parse(matcher.group(3)
                                                + "-" + matcher.group(4)
                                                + "-" + matcher.group(5));
        }
        return LocalDateTime.of(localDate, LocalTime.MIN).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
    }

    public Calendar getAcquisitionDate() {
        Calendar calendar = Calendar.getInstance();
        LocalDate localDate;
        Matcher matcher;
        if (this.format == 1) {
            matcher = preCollectionNamePattern.matcher(name);
            //noinspection ResultOfMethodCallIgnored
            matcher.matches();
            localDate = Year.of(Integer.parseInt(matcher.group(3))).atDay(Integer.parseInt(matcher.group(4)));
        } else {
            matcher = collectionNamePattern.matcher(name);
            //noinspection ResultOfMethodCallIgnored
            matcher.matches();
            localDate = LocalDate.parse(matcher.group(3)
                                                + "-" + matcher.group(4)
                                                + "-" + matcher.group(5));
        }
        calendar.setTime(Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));
        return calendar;
    }

    @Override
    public String getOrbit() { return "-1"; }

    @Override
    protected boolean verifyProductName(String name) {
        this.format = preCollectionNamePattern.matcher(name).matches()
                ? 1
                : collectionNamePattern.matcher(name).matches()
                    ? name.contains("_01_")
                        ? 2
                        : 3
                    : 0;
        return this.format > 0;
    }
}
