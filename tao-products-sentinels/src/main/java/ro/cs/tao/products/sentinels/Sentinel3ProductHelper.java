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
public class Sentinel3ProductHelper extends ProductHelper {

    static final Pattern S3Pattern =
            Pattern.compile("(S3[A-B])_(OL)_(\\d{1})_(EFR___)_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(\\d{8}T\\d{6})_(\\d{4})_(\\d{3})_(\\d{3})_(\\d{4})_(\\w{3})_(\\w{1})_(\\w{2})_(\\d{3})(?:.SAFE)?");

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
    public String getOrbit() { return "-1"; }

    @Override
    protected boolean verifyProductName(String name) {
        return S3Pattern.matcher(name).matches();
    }

}
