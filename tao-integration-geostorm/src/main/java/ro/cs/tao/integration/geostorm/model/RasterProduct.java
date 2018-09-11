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

package ro.cs.tao.integration.geostorm.model;

import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.Geometry;

import java.io.IOException;

public class RasterProduct {

    // product_path: path to the product, type unicode
    private String product_path;

    // owner: product owner name, type unicode
    private String owner;

    // collection: product collection, type unicode
    private String collection;

    // site: indication of the geographical location of the product, type unicode
    private String site;

    // mosaic_name: name of the mosaic linked to the product, type unicode
    private String mosaic_name;

    // entry_point: list of relatives paths to files in product, type list
    private String[] entry_point;

    // product_date: date of product creation, type Date
    private String product_date;

    // extent: geographic hold of product, type Polygon
    /**
     'extent': {
        'type': 'Polygon',
        'coordinates': [
                        [[-180, -60], [-180, 60], [180, 60], [180, -60], [-180, -60]]
                       ]
               }
     */
    private Geometry extent;

    // organization: resource producer, type unicode
    private String organization;

    // band_filter: optional product band filter and band order configuration eg [3, 2, 1], type list(int)
    //private Integer[] band_filter;

    public RasterProduct() {
    }

    public String getProduct_path() {
        return product_path;
    }

    public void setProduct_path(String product_path) {
        this.product_path = product_path;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getMosaic_name() {
        return mosaic_name;
    }

    public void setMosaic_name(String mosaic_name) {
        this.mosaic_name = mosaic_name;
    }

    public String[] getEntry_point() {
        return entry_point;
    }

    public void setEntry_point(String[] entry_point) {
        this.entry_point = entry_point;
    }

    public String getProduct_date() {
        return product_date;
    }

    public void setProduct_date(String product_date) {
        this.product_date = product_date;
    }

    public String getExtent() {
        GeometryJSON g = new GeometryJSON();
        return g.toString(extent);
    }
    public void setExtent(String extentAsJson) {
        GeometryJSON g = new GeometryJSON();
        try {
            extent = g.read(extentAsJson);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    /*public Integer[] getBand_filter() {
        return band_filter;
    }

    public void setBand_filter(Integer[] band_filter) {
        this.band_filter = band_filter;
    }*/
}
