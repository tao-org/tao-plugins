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

import ro.cs.tao.datasource.remote.ProductHelper;

import java.util.regex.Pattern;

/**
 * @author Cosmin Cara
 */
public class Sentinel1ProductHelper extends ProductHelper {

    /**
     * Tokens:
     * 0: Mission identifier
     * 1: Mode/Beam
     * 2: Product type
     * 3: Resolution class
     * 4: Processing level
     * 5: Product class
     * 6: Polarisation
     * 7: Start time
     * 8: Stop time
     * 9: Absolute orbit number
     * 10: Mission datatake identifier
     * 11: Product unique identifier CRC-16
     * 12: (optional) folder extension
     */
    static final Pattern S1Pattern =
            Pattern.compile("(S1[A-B])_(SM|IW|EW|WV)_(SLC|GRD|RAW|OCN)([FHM_])_([0-2])([AS])(SH|SV|DH|DV)_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(\\d{6})_([0-9A-F]{6})_([0-9A-F]{4})(?:.SAFE)?");

    Sentinel1ProductHelper() { super(); }

    Sentinel1ProductHelper(String productName) { super(productName); }

    @Override
    public String getSensingDate() {
        return getTokens(S1Pattern, this.name, null)[7].substring(0, 8);
    }

    @Override
    public String getProductRelativePath() {
        return null;
    }

    @Override
    public Pattern getTilePattern() { return null; }

    @Override
    public String getMetadataFileName() { return "manifest.safe"; }

    public String getOrbit() {
        String[] tokens = getTokens(S1Pattern, this.name, null);
        int absOrbit = Integer.parseInt(tokens[9]);
        return String.format("%03d",
                             tokens[0].endsWith("A") ?
                                     ((absOrbit - 73) % 175) + 1 :
                                     ((absOrbit - 27) % 175) + 1);
    }

    public PolarisationMode getPolarisation() {
        return Enum.valueOf(PolarisationMode.class, getTokens(S1Pattern, this.name, null)[6]);
    }

    public ProductType getProductType() {
        return Enum.valueOf(ProductType.class, getTokens(S1Pattern, this.name, null)[2]);
    }

    public Resolution getResolutionClass() {
        return Enum.valueOf(Resolution.class, getTokens(S1Pattern, this.name, null)[3]);
    }

    @Override
    protected boolean verifyProductName(String name) {
        return S1Pattern.matcher(name).matches();
    }

    public enum PolarisationMode {
        SH, SV, DH, DV
    }

    public enum ProductType {
        RAW, SLC, GRD, OCN
    }

    public enum Resolution {
        F, H, M, NA
    }
}
