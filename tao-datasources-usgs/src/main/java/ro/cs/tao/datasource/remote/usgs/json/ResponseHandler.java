package ro.cs.tao.datasource.remote.usgs.json;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.Polygon2D;
import ro.cs.tao.eodata.enums.DataFormat;
import ro.cs.tao.eodata.enums.PixelType;
import ro.cs.tao.eodata.enums.SensorType;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Cosmin Cara
 */
public class ResponseHandler implements JSonResponseHandler<EOProduct> {
    @Override
    public List<EOProduct> readValues(String content, AttributeFilter... filters) throws IOException {
        List<EOProduct> results = new ArrayList<>();
        final JsonReader jsonReader = Json.createReader(new StringReader(content));
        JsonObject responseObj = jsonReader.readObject();
        JsonArray jsonArray = responseObj.getJsonArray("results");
        for (int i = 0; i < jsonArray.size(); i++) {
            EOProduct currentProduct = new EOProduct();
            currentProduct.setFormatType(DataFormat.RASTER);
            currentProduct.setSensorType(SensorType.OPTICAL);
            currentProduct.setPixelType(PixelType.UINT16);
            JsonObject result = jsonArray.getJsonObject(i);
            currentProduct.setName(result.getString("product_id"));
            currentProduct.setId(result.getString("scene_id"));
            try {
                currentProduct.setAcquisitionDate(new SimpleDateFormat("yyyy-MM-dd").parse(result.getString("acquisitionDate")));
            } catch (Exception e) {
                e.printStackTrace();
            }
            Polygon2D footprint = new Polygon2D();
            footprint.append(result.getJsonNumber("upperLeftCornerLongitude").doubleValue(),
                             result.getJsonNumber("upperLeftCornerLatitude").doubleValue());
            footprint.append(result.getJsonNumber("upperRightCornerLongitude").doubleValue(),
                             result.getJsonNumber("upperRightCornerLatitude").doubleValue());
            footprint.append(result.getJsonNumber("lowerRightCornerLongitude").doubleValue(),
                             result.getJsonNumber("lowerRightCornerLatitude").doubleValue());
            footprint.append(result.getJsonNumber("lowerLeftCornerLongitude").doubleValue(),
                             result.getJsonNumber("lowerLeftCornerLatitude").doubleValue());
            footprint.append(result.getJsonNumber("upperLeftCornerLongitude").doubleValue(),
                             result.getJsonNumber("upperLeftCornerLatitude").doubleValue());
            currentProduct.setGeometry(footprint.toWKT());
            try {
                currentProduct.setLocation(result.getJsonObject("download_links").getString("usgs"));
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            results.add(currentProduct);
        }
        return results;
    }
}
