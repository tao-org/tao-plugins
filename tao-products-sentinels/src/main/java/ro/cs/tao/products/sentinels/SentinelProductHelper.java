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

/**
 * Utility class responsible for creating instances of specific product helper classes
 * based on the given product name. This class provides a factory method to return
 * the appropriate helper instance for Sentinel products, such as Sentinel-1, Sentinel-3,
 * and Sentinel-5P, or their corresponding orbit files.
 *
 * The class identifies the type of product by matching the product name against
 * predefined patterns in the respective Sentinel product helper classes. If no specific
 * match is found, the factory falls back to creating a Sentinel-2 product helper.
 *
 * This is a static utility class and does not require instantiation.
 */
public class SentinelProductHelper {

    public static ProductHelper create(String productName) {
        if (Sentinel1ProductHelper.S1Pattern.matcher(productName).matches()) {
            return new Sentinel1ProductHelper(productName);
        } else if (Sentinel3ProductHelper.S3Pattern.matcher(productName).matches()) {
            return new Sentinel3ProductHelper(productName);
        } else if (Sentinel5PProductHelper.S5PPattern.matcher(productName).matches()) {
            return new Sentinel5PProductHelper(productName);
        } else if (Sentinel1OrbitFileHelper.S1AuxPattern.matcher(productName).matches()) {
            return new Sentinel1OrbitFileHelper(productName);
        } else {
            return Sentinel2ProductHelper.createHelper(productName);
        }
    }
}
