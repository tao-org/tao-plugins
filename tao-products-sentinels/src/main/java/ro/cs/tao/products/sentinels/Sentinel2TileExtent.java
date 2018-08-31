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

import ro.cs.tao.datasource.util.TileExtent;
import ro.cs.tao.eodata.Polygon2D;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.LogManager;

/**
 * @author Cosmin Cara
 */
public class Sentinel2TileExtent extends TileExtent {

    private static final Sentinel2TileExtent instance;

    static {
        instance = new Sentinel2TileExtent();
        try {
            LogManager.getLogManager().getLogger("").info("Loading Sentinel-2 tiles extents");
            instance.read(Sentinel2TileExtent.class.getResourceAsStream("S2tilemap.dat"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Sentinel2TileExtent getInstance() {
        return instance;
    }

    private Sentinel2TileExtent() { super(); }

    @Override
    public void fromKml(BufferedReader bufferedReader) throws IOException {
        try {
            String line;
            String tileCode = null;
            boolean inElement = false;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains("<Placemark>")) {
                    inElement = true;
                } else {
                    if (inElement && line.contains("<name>")) {
                        int i = line.indexOf("<name>");
                        tileCode = line.substring(i + 6, i + 11);
                    }
                    if (inElement && !line.trim().startsWith("<")) {
                        String[] tokens = line.trim().split(" ");
                        Polygon2D polygon = new Polygon2D();
                        for (String point : tokens) {
                            String[] coords = point.split(",");
                            polygon.append(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
                        }
                        tiles.put(tileCode, polygon.getBounds2D());
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
