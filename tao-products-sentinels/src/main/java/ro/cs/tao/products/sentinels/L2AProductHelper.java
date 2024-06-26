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

import java.util.regex.Pattern;

/**
 * @author Cosmin Cara
 */
public class L2AProductHelper extends Sentinel2ProductHelper {

    static final Pattern ProductV14 = Pattern.compile("(S2[A-B])_(MSIL2A)_(\\d{8}T\\d{6})_(N\\d{4})_R(\\d{3})_T(\\d{2}\\w{3})_(\\d{8}T\\d{6})(?:.SAFE)?");

    public L2AProductHelper() {
        super();
    }

    L2AProductHelper(String name) {
        super(name);
        this.version = PSD_14;
    }

    @Override
    public L2AProductHelper duplicate() {
        return new L2AProductHelper(getName());
    }

    @Override
    public String getVersion() {
        if (this.version == null) {
            this.version = PSD_14;
        }
        return this.version;
    }

    @Override
    public String getProcessingDate() {
        return getTokens(ProductV14)[6];
    }

    @Override
    public String getSensingDate() {
        return getTokens(ProductV14)[2];
    }

    @Override
    public String getProductRelativePath() {
        String year, day, month;

        String[] tokens = getTokens(ProductV14);
        String dateToken = tokens[2];
        year = dateToken.substring(0, 4);
        month = String.valueOf(Integer.parseInt(dateToken.substring(4, 6)));
        day = String.valueOf(Integer.parseInt(dateToken.substring(6, 8)));

        return year + URL_SEPARATOR
                + month + URL_SEPARATOR
                + day + URL_SEPARATOR
                + this.name + URL_SEPARATOR;
    }

    @Override
    public String getTileIdentifier() {
        return getTokens(ProductV14)[5];
    }

    @Override
    public String getMetadataFileName() {
        return "MTD_MSIL2A.xml";
    }

    @Override
    public String getDatastripMetadataFileName(String datastripIdentifier) {
        return "MTD_DS.xml";
    }

    @Override
    public String getDatastripFolder(String datastripIdentifier) {
        return datastripIdentifier.substring(17, 57);
    }

    @Override
    public String getGranuleFolder(String datastripIdentifier, String granuleIdentifier) {
        return granuleIdentifier.substring(13, 16) + "_" +
                granuleIdentifier.substring(49, 55) + "_" +
                granuleIdentifier.substring(41, 48) + "_" +
                datastripIdentifier.substring(42, 57);
    }

    @Override
    public String getGranuleMetadataFileName(String granuleIdentifier) {
        return "MTD_TL.xml";
    }

    @Override
    public String getBandFileName(String granuleIdentifier, String band) {
        String[] tokens;
        String prodName = this.name.endsWith(".SAFE") ? this.name.substring(0, this.name.length() - 5) : this.name;
        tokens = getTokens(ProductV14, prodName, null);
        return "L2A_" + tokens[5] + "_" + tokens[2] + "_" + band;
    }

    @Override
    public String getEcmWftFileName(String granuleIdentifier) {
        return "AUX_ECMWFT";
    }

    @Override
    public String getOrbit() {
        return getTokens(ProductV14)[4];
    }

    @Override
    protected boolean verifyProductName(String name) {
        return ProductV14.matcher(name).matches();
    }
}
