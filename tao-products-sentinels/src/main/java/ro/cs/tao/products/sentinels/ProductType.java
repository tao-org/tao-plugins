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

/**
 * Represents the type of a product with corresponding identifiers.
 * This enum provides predefined constants for different product types:
 *
 * L1C - Represents the product type "S2MSI1C".
 * L2Ap - Represents the product type "S2MSI2Ap".
 * L2A - Represents the product type "S2MSI2A".
 *
 * Each product type is associated with a string value that can be
 * retrieved or represented via the overridden toString method.
 */
public enum ProductType {
    L1C("S2MSI1C"),
    L2Ap("S2MSI2Ap"),
    L2A("S2MSI2A");

    private String value;
    ProductType(String value) { this.value = value; }

    @Override
    public String toString() {
        return this.value;
    }
}
