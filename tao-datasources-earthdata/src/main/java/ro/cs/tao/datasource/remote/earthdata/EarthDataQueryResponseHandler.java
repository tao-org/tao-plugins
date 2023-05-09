package ro.cs.tao.datasource.remote.earthdata;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;

import javax.json.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class EarthDataQueryResponseHandler implements JSonResponseHandler<EOProduct> {

    public List<EOProduct> readValues(String content, AttributeFilter...filters) throws IOException {
        List<EOProduct> results = new ArrayList<>();
        JsonReader reader = Json.createReader(new StringReader(content));
        JsonObject rootObject = reader.readObject();

        JsonObject feedObject = rootObject.getJsonObject("feed");
        JsonArray jsonArray = feedObject.getJsonArray("entry");

        for (int i = 0; i< jsonArray.size(); i++) {
            try {
                boolean hasDownloadLink = false;
                JsonObject jsonObject = jsonArray.getJsonObject(i);
                EOProduct result = new EOProduct();
                result.setId(jsonObject.getString("id"));
                result.setFormatType(DataFormat.RASTER);
                JsonArray links = jsonObject.getJsonArray("links");
                for (int indexLink = 0; indexLink < links.size() ; indexLink++){
                    JsonObject currentLink = links.getJsonObject(indexLink);
                    String url = currentLink.getString("href");
                    if(url.matches(".*(h5|nc|nc4)$") && (!url.contains(".xml") && !url.contains(".iso")
                            && !url.contains(".html") && !url.contains(".pdf"))){
                        result.setLocation(url);
                        String filename = Paths.get(new URI(url).getPath()).getFileName().toString();
                        result.setName(removeExtension(filename));
                        hasDownloadLink = true;
                        break;
                    }
                }
                result.setGeometry(createFootprint(jsonObject));
                result.setApproximateSize((long)Float.parseFloat(jsonObject.getString("granule_size")));
                result.setProductType(jsonObject.getString("original_format"));
                if (hasDownloadLink) {
                    results.add(result);
                }
            } catch (Exception ex) {
                throw new IOException(ex.getMessage());
            }
        }

        return results;
    }

    private static String removeExtension(String fileName) {
        if (fileName.indexOf(".") > 0) {
            return fileName.substring(0, fileName.lastIndexOf("."));
        } else {
            return fileName;
        }
    }

    private String createFootprint(JsonObject jsonObject) {
        Polygon2D footprint = new Polygon2D();
        // footprint can be obtained from boxes or polygons object
        if (jsonObject.getJsonArray("boxes") != null && jsonObject.getJsonArray("boxes").size() > 0) {
            String coordinates = jsonObject.getJsonArray("boxes").getString(0).replace("\"", "");
            String points[] = coordinates.split("\\s");
            int length = points.length;
            if (length == 4) {
                double latMin = Double.parseDouble(points[0]);
                double lonMin = Double.parseDouble(points[1]);
                double latMax = Double.parseDouble(points[2]);
                double lonMax = Double.parseDouble(points[3]);
                footprint.append(latMin, lonMin);
                footprint.append(latMin, lonMax);
                footprint.append(latMax, lonMax);
                footprint.append(latMax, lonMin);
                footprint.append(latMin, lonMin);
            } else {
                double currentCoordinate;
                double nextCoordinate;
                for (int j = 0; j < length - 1; j++) {
                    currentCoordinate = Double.parseDouble(points[j].trim());
                    nextCoordinate = Double.parseDouble(points[j+1].trim());
                    j++;
                    footprint.append(currentCoordinate, nextCoordinate);
                }
            }
        } else if (jsonObject.getJsonArray("polygons") != null && jsonObject.getJsonArray("polygons").size() > 0) {
           JsonArray polygons = jsonObject.getJsonArray("polygons");
            int length = polygons.size();
            if (length == 1) {
                JsonValue coordinates = polygons.get(0).asJsonArray().get(0);
                String points[] = coordinates.toString().replaceAll("\"","").split("\\s");
                double currentCoordinate;
                double nextCoordinate;
                for (int j = 0; j < points.length - 1; j++) {
                    currentCoordinate = Double.parseDouble(points[j].trim());
                    nextCoordinate = Double.parseDouble(points[j + 1].trim());
                    j++;
                    footprint.append(currentCoordinate, nextCoordinate);
                }
            } else {
                int countPolygons = 0;
                for (JsonValue polygon: polygons) {
                    JsonValue coordinates = polygon.asJsonArray().get(0);
                    String points[] = coordinates.toString().replaceAll("\"","").split("\\s");
                    double currentCoordinate;
                    double nextCoordinate;
                    for (int j = 0; j < points.length - 1; j++) {
                        currentCoordinate = Double.parseDouble(points[j].trim());
                        nextCoordinate = Double.parseDouble(points[j + 1].trim());
                        j++;
                        footprint.append(countPolygons, currentCoordinate, nextCoordinate);
                    }
                    countPolygons++;
                }
            }
        }
        return footprint.toWKT();
    }
}
