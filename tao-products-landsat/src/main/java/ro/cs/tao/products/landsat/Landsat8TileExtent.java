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

import ro.cs.tao.datasource.util.TileExtent;
import ro.cs.tao.eodata.Polygon2D;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.LogManager;

/**
 * Map of Landsat8 tile extents. The initial map can be created from the official wrt_descending.shp converted to KML.
 *
 * @author Cosmin Cara
 */
public class Landsat8TileExtent extends TileExtent {
    private static final Landsat8TileExtent instance;

    static {
        instance = new Landsat8TileExtent();
        try {
            LogManager.getLogManager().getLogger("").info("Loading Landsat8 tiles extents");
            instance.read(Landsat8TileExtent.class.getResourceAsStream("L8tilemap.dat"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Landsat8TileExtent getInstance() {
        return instance;
    }

    private Landsat8TileExtent() { super(); }

    @Override
    public void fromKml(BufferedReader bufferedReader) throws IOException {
        try {
            String line;
            String path = null, row = null;
            boolean inElement = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("<Placemark>")) {
                    inElement = true;
                } else {
                    if (inElement && line.contains("name=\"PATH\"")) {
                        int i = line.indexOf("name=\"PATH\"");
                        path = line.substring(i + 12, line.indexOf("</"));
                        path = ("000" + path).substring(path.length());
                    }
                    if (inElement && line.contains("name=\"ROW\"")) {
                        int i = line.indexOf("name=\"ROW\"");
                        row = line.substring(i + 11, line.indexOf("</"));
                        row = ("000" + row).substring(row.length());
                    }
                    if (inElement && !line.trim().startsWith("<")) {
                        String[] tokens = line.trim().split(" ");
                        Polygon2D polygon = new Polygon2D();
                        for (String point : tokens) {
                            String[] coords = point.split(",");
                            polygon.append(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
                        }
                        tiles.put(path + row, polygon.getBounds2D());
                        inElement = false;
                    }
                }
            }
        } finally {
            if (bufferedReader != null)
                bufferedReader.close();
        }
    }
}
