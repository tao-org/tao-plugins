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

import ro.cs.tao.eodata.util.BaseProductHelper;

import java.util.regex.Pattern;

/**
 * @author Cosmin Cara
 */
public abstract class Sentinel2ProductHelper extends BaseProductHelper {

    public static final String PSD_13 = "13";
    public static final String PSD_14 = "14";

    protected double cloudsPercentage;

    public static Sentinel2ProductHelper createHelper(String productName) {
        Sentinel2ProductHelper helper = null;
        try {
            helper = new L1CProductHelper(productName);
        } catch (IllegalArgumentException e) {
            helper = new L2AProductHelper(productName);
        }
        return helper;
    }

    public Sentinel2ProductHelper() { super(); }

    Sentinel2ProductHelper(String name) {
        super(name);
    }

    @Override
    public int order() {
        return 0;
    }

    public double getCloudsPercentage() { return cloudsPercentage; }

    public void setCloudsPercentage(double cloudsPercentage) {
        this.cloudsPercentage = cloudsPercentage;
    }

    @Override
    public Pattern getTilePattern() { return Pattern.compile("(?:.+)(\\d{2}[A-Z]{3})(?:.+)"); }

    public abstract String getTileIdentifier();

    public abstract String getDatastripMetadataFileName(String datastripIdentifier);

    public abstract String getDatastripFolder(String datastripIdentifier);

    public abstract String getGranuleFolder(String datastripIdentifier, String granuleIdentifier);

    public abstract String getGranuleMetadataFileName(String granuleIdentifier);

    public abstract String getBandFileName(String granuleIdentifier, String band);

    public abstract String getEcmWftFileName(String granuleIdentifier);
}
