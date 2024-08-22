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
public class Sentinel3ProductHelper extends BaseProductHelper {

    static final Pattern S3Pattern =
            Pattern.compile("(S3[AB_])_(OL|SR|SL|ST|SY|DO|MW|GN|AX)_([012_])_(EFR\\w{3}|CR0\\w{3}|CR1\\w{3}|ERR\\w{3}|RAC\\w{3}|SPC\\w{3}|INS\\w{3}|WFR\\w{3}|WRR\\w{3}|LFR\\w{3}|LRR\\w{3}|ATP\\w{3}|AER\\w{3}|LAP\\w{3}|LVI\\w{3}|SLT\\w{3}|RBT\\w{3}|WCT\\w{3}|WST\\w{3}|LST\\w{3}|MISR__|SYN\\w{3}|VGP\\w{3}|VG1\\w{3}|V10\\w{3}|LAN\\w{3}|WAT\\w{3}|SRA\\w{3}|CAL\\w{3}|FRP\\w{3})_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(((\\d{4})_(\\d{3})_(\\d{3})_(\\d{4}|____))|(\\w{17}))_(\\w{3})_([DFOR])_(NR|ST|NT)_(\\d{3})(?:.SEN3)?");

    public Sentinel3ProductHelper() { super(); }

    Sentinel3ProductHelper(String productName) { super(productName); }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public Sentinel3ProductHelper duplicate() {
        return new Sentinel3ProductHelper(getName());
    }

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
