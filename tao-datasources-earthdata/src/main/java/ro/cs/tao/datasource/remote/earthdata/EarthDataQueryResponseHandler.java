package ro.cs.tao.datasource.remote.earthdata;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;
import ro.cs.tao.utils.DateUtils;

import javax.json.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class EarthDataQueryResponseHandler implements JSonResponseHandler<EOProduct> {

    private final Predicate<EOProduct> filter;
    private final SensorType sensorType;

    public EarthDataQueryResponseHandler(SensorType type, Predicate<EOProduct> filter) {
        this.filter = filter;
        this.sensorType = type;
    }

    public List<EOProduct> readValues(String content, AttributeFilter...filters) throws IOException {
        List<EOProduct> results = new ArrayList<>();
        JsonReader reader = Json.createReader(new StringReader(content));
        JsonObject rootObject = reader.readObject();

        JsonObject feedObject = rootObject.getJsonObject("feed");
        JsonArray jsonArray = feedObject.getJsonArray("entry");
        final int size = jsonArray.size();
        for (int i = 0; i < size; i++) {
            try {
                boolean hasDownloadLink = false;
                JsonObject jsonObject = jsonArray.getJsonObject(i);
                EOProduct result = new EOProduct();
                result.setSensorType(this.sensorType);
                result.setId(jsonObject.getString("id"));
                result.setFormatType(DataFormat.RASTER);
                result.setPixelType(PixelType.UNKNOWN);
                JsonArray links = jsonObject.getJsonArray("links");
                for (int indexLink = 0; indexLink < links.size() ; indexLink++){
                    JsonObject currentLink = links.getJsonObject(indexLink);
                    String url = currentLink.getString("href");
                    if(url.matches(".*(h5|hdf|nc|nc4|tif)$") && (!url.contains(".xml") && !url.contains(".iso")
                            && !url.contains(".html") && !url.contains(".pdf"))){
                        result.setLocation(url);
                        String filename = Paths.get(new URI(url).getPath()).getFileName().toString();
                        result.setName(removeExtension(filename));
                        hasDownloadLink = true;
                        break;
                    }
                }
                result.setGeometry(createFootprint(jsonObject));
                if (this.filter != null && this.filter.test(result)) {
                    continue;
                }
                if (jsonObject.containsKey("granule_size")) {
                    result.setApproximateSize((long) Float.parseFloat(jsonObject.getString("granule_size")));
                } else {
                    result.setApproximateSize(-1);
                }
                result.setAcquisitionDate(DateUtils.parseDateTime(jsonObject.getString("time_end")));
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
        if (jsonObject.getJsonArray("boxes") != null && !jsonObject.getJsonArray("boxes").isEmpty()) {
            String coordinates = jsonObject.getJsonArray("boxes").getString(0).replace("\"", "");
            String[] points = coordinates.split("\\s");
            int length = points.length;
            if (length == 4) {
                double latMin = Double.parseDouble(points[0].trim());
                double lonMin = Double.parseDouble(points[1].trim());
                double latMax = Double.parseDouble(points[2].trim());
                double lonMax = Double.parseDouble(points[3].trim());
                footprint.append(lonMin, latMin);
                footprint.append(lonMax, latMin);
                footprint.append(lonMax, latMax);
                footprint.append(lonMin, latMax);
                footprint.append(lonMin, latMin);
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
        } else if (jsonObject.getJsonArray("polygons") != null && !jsonObject.getJsonArray("polygons").isEmpty()) {
           JsonArray polygons = jsonObject.getJsonArray("polygons");
            int length = polygons.size();
            if (length == 1) {
                JsonValue coordinates = polygons.get(0).asJsonArray().get(0);
                String[] points = coordinates.toString().replaceAll("\"","").split("\\s");
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
                    String[] points = coordinates.toString().replaceAll("\"","").split("\\s");
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
