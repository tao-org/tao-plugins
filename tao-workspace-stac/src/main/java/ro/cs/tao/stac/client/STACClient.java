package ro.cs.tao.stac.client;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import ro.cs.tao.datasource.QueryException;
import ro.cs.tao.datasource.remote.result.json.JsonResponseParser;
import ro.cs.tao.stac.model.*;
import ro.cs.tao.utils.HttpMethod;
import ro.cs.tao.utils.NetUtils;

import java.util.List;
import java.util.logging.Logger;

public class STACClient {
    private final String endpoint;
    private final Logger logger;

    public STACClient(String endpoint) {
        this.endpoint = endpoint;
        this.logger = Logger.getLogger(STACClient.class.getName());
    }

    public Catalogs listCollections(String relativePath) {
        return doRequest(this.endpoint + (relativePath != null ? relativePath : ""),
                         new JsonResponseParser<>(new CatalogsResponseHandler()));
    }

    public Collection describeCatalog(Link catalogLink) {
        if (catalogLink == null
                || "child".equalsIgnoreCase(catalogLink.getRel())
                || !catalogLink.getHref().startsWith(this.endpoint)) {
            throw new IllegalArgumentException("Invalid catalog link");
        }
        return doRequest(catalogLink.getHref(), new JsonResponseParser<>(new CollectionResponseHandler()));
    }

    public List<Feature> listItems(String itemsLink) {
        List<Feature> items = null;
        FeatureCollection collection = doRequest(itemsLink, new JsonResponseParser<>(new ItemsResponseHandler()));
        if (collection != null) {
            items = collection.getFeatures();
        }
        return items;
    }

    private <T> T doRequest(String url, JsonResponseParser<T> parser) {
        T result = null;
        try (CloseableHttpResponse response = NetUtils.openConnection(HttpMethod.GET, url, null)) {
            switch (response.getStatusLine().getStatusCode()) {
                case 200:
                    String body = EntityUtils.toString(response.getEntity());
                    result = parser.parseValue(body);
                    break;
                case 401:
                    throw new QueryException("The supplied credentials are invalid!");
                default:
                    throw new QueryException(String.format("The request was not successful. Reason: %s",
                            response.getStatusLine().getReasonPhrase()));
            }
        } catch (Exception ex) {
            logger.severe(ex.getMessage());
        }
        return result;
    }
}
