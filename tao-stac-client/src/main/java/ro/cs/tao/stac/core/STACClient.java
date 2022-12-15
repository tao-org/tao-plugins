package ro.cs.tao.stac.core;

import org.geotools.http.HTTPResponse;
import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.parser.STACParser;
import ro.cs.tao.utils.FileUtilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class STACClient {
    private final URL stacURL;
    final HttpClient client;

    public STACClient(String baseURL, WebServiceAuthentication authentication) throws MalformedURLException {
        this.stacURL = new URL(baseURL);
        this.client= new HttpClient(authentication);
    }

    public Catalog getCatalog() throws IOException {
        final HTTPResponse response = this.client.get(this.stacURL);
        try (InputStream inStream = response.getResponseStream()) {
            return new STACParser().parseCatalogResponse(inStream);
        }
    }

    public CollectionList listCollections() throws IOException {
        final HTTPResponse response = this.client.get(new URL(this.stacURL + "/collections"));
        try (InputStream inStream = response.getResponseStream()) {
            return new STACParser().parseCollectionsResponse(inStream);
        }
    }

    public Collection getCollection(String collectionName) throws IOException {
        final HTTPResponse response = this.client.get(new URL(this.stacURL + "/collections/" + collectionName));
        try (InputStream inStream = response.getResponseStream()) {
            return new STACParser().parseCollectionResponse(inStream);
        }
    }

    public ItemCollection listItems(String collectionName) throws IOException {
        return listItems(collectionName, 0, 0);
    }

    public ItemCollection listItems(String collectionName, int pageNumber, int pageSize) throws IOException {
        String href = this.stacURL + "/collections/" + collectionName + "/items";
        if (pageNumber > 0 & pageSize > 0) {
            href += "?page=" + pageSize + "&limit=" + pageSize;
        }
        final HTTPResponse response = this.client.get(new URL(href));
        try (InputStream inStream = response.getResponseStream()) {
            return new STACParser().parseItemCollectionResponse(inStream);
        }
    }

    public Item getItem(String collectionName, String itemId) throws IOException {
        String href = this.stacURL + "/collections/" + collectionName + "/items/" + itemId;
        final HTTPResponse response = this.client.get(new URL(href));
        try (InputStream inStream = response.getResponseStream()) {
            return new STACParser().parseItemResponse(inStream);
        }
    }

    public ItemCollection search(String collectionName, Map<String, Object> parameters) throws IOException {
        return search(collectionName, parameters, 0, 0);
    }

    public ItemCollection search(String collectionName, Map<String, Object> parameters, int pageNumber, int pageSize) throws IOException {
        if (parameters == null) {
            return listItems(collectionName);
        }
        final Catalog catalog = getCatalog();
        Link searchLink = catalog.getLinks().stream().filter(l -> "search".equals(l.getRel())).findFirst().orElse(null);
        if (searchLink == null) {
            throw new IOException("Search not supported on catalog " + catalog.getId());
        }
        StringBuilder href = new StringBuilder(searchLink.getHref() + "?");
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            href.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        href.append("collections=").append(collectionName).append("&");
        if (pageNumber > 0 & pageSize > 0) {
            href.append("page=").append(pageSize).append("&limit=").append(pageSize);
        }
        if (href.charAt(href.length() - 1) == '&') {
            href.setLength(href.length() - 1);
        }
        final HTTPResponse response = this.client.get(new URL(href.toString()));
        try (InputStream inStream = response.getResponseStream()) {
            return new STACParser().parseItemCollectionResponse(inStream);
        }
    }

    public void download(Item item, Path folder) throws IOException {
        final Map<String, Asset> assets = item.getAssets();
        if (assets != null && assets.size() > 0) {
            final Path target = folder.resolve(item.getId());
            Files.createDirectories(target);
            for (Asset asset : assets.values()) {
                download(asset, target);
            }
        }
    }

    public void download(Asset asset, Path folder) throws IOException {
        final String href = asset.getHref();
        final String name = href.substring(href.lastIndexOf('/'));
        final HTTPResponse response = this.client.get(new URL(href));
        try (InputStream inStream = response.getResponseStream();
             OutputStream outStream = Files.newOutputStream(folder.resolve(name))) {
            FileUtilities.copyStream(inStream, outStream);
        }
    }
    public InputStream download(Asset asset) throws IOException {
        final HTTPResponse response = this.client.get(new URL(asset.getHref()));
        return response.getResponseStream();
    }

}
