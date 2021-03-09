package ro.cs.tao.datasource.remote.earthdata;

import ro.cs.tao.datasource.remote.result.filters.AttributeFilter;
import ro.cs.tao.datasource.remote.result.json.JSonResponseHandler;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.eodata.enums.DataFormat;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
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
}
