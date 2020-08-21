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

import ro.cs.tao.eodata.util.ProductHelper;

import java.util.regex.Pattern;

/**
 * @author Cosmin Cara
 */
public class Sentinel3ProductHelper extends ProductHelper {

    static final Pattern S3Pattern =
            Pattern.compile("(S3[AB_])_(OL|SR|SL|ST|SY|DO|MW|GN|AX)_([012_])_(EFR___|CR0___|CR1___|ERR___|RAC___|SPC___|INS_AX|EFR_BW|ERR_BW|WFR___|WRR___|LFR___|LRR___|ATP_AX|AER_AX|LAP_AX|LVI_AX|WFR_BW|WRR_BW|LFR_BW|LRR_BW|SLT___|RBT___|RBT_BW|WCT___|WST___|LST___|WST_BW|LST_BW|MISR__|SYN___|VGP___|VG1___|V10___|SYN_BW|VGP_BW|VG1_BW|V10_BW|LAN___|WAT___|SRA___|CAL___|FRP___)_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(((\\d{4})_(\\d{3})_(\\d{3})_(\\d{4}|____))|(\\w{17}))_(\\w{3})_([DFOR])_(NR|ST|NT)_(\\d{3})(?:.SEN3)?");

    Sentinel3ProductHelper() { super(); }

    Sentinel3ProductHelper(String productName) { super(productName); }

    @Override
    public String getProductRelativePath() {
        return null;
    }

    @Override
    public Pattern getTilePattern() { return null; }

    @Override
    public String getMetadataFileName() { return "xfdumanifest.xml"; }

    @Override
    public String getOrbit() {
        return getTokens(S3Pattern, this.name, null)[9];
    }

    @Override
    public String getSensingDate() {
        return getTokens(S3Pattern, this.name, null)[4];
    }

    @Override
    public String getProcessingDate() {
        return getTokens(S3Pattern, this.name, null)[6];
    }

    @Override
    protected boolean verifyProductName(String name) {
        return S3Pattern.matcher(name).matches();
    }

}
