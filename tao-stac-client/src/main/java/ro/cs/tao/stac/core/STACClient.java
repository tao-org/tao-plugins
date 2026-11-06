package ro.cs.tao.stac.core;

import org.geotools.http.HTTPResponse;
import ro.cs.tao.ListenableInputStream;
import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.stac.core.model.*;
import ro.cs.tao.stac.core.parser.STACParser;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.monitoring.DownloadProgressListener;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Client for a STAC web service.
 * It allows for:
 * - browsing various STAC items (catalog, collections, items)
 * - searching collections
 * - downloading individual assets or full items
 *
 * @author Cosmin Cara
 */
public class STACClient {
    private final URL stacURL;
    final HttpClient client;
    private DownloadProgressListener progressListener;
    protected final Logger logger = Logger.getLogger(getClass().getName());

    /**
     * Initializes a new client for the given URL, with specific authentication instructions.
     * @param baseURL           The URL of the STAC web service
     * @param authentication    The authentication scheme
     */
    public STACClient(String baseURL, WebServiceAuthentication authentication) throws MalformedURLException {
        this.stacURL = new URL(baseURL);
        this.client= new HttpClient(authentication);
    }

    /**
     * Retrieves the catalog description from the remote STAC service
     */
    public Catalog getCatalog() throws IOException {
        final HTTPResponse response = this.client.get(this.stacURL);
        try (InputStream inStream = response.getResponseStream()) {
            return STACParser.parseCatalogResponse(inStream);
        }
    }
    /**
     * Retrieves the list of collection descriptions from the remote STAC service (all pages)
     */
    public CollectionList listCollections() throws IOException {
        List<Collection> allCollections = new ArrayList<>();
        String url = this.stacURL + "/collections";
        CollectionList cumulatedResponse = new CollectionList();
        CollectionList page;

        while (url != null) {
            final HTTPResponse response = this.client.get(new URL(url));
           try (InputStream inStream = response.getResponseStream()) {
                page = STACParser.parseCollectionsResponse(inStream);
                // Add current page results
                allCollections.addAll(page.getCollections());
                // Find "next" link
                url = null;
                if (page.getLinks() != null) {
                    for (Link link : page.getLinks()) {
                        if ("next".equals(link.getRel())) {
                            url = link.getHref();
                            // Handle relative URLs
                            if (url != null && !url.startsWith("http")) {
                                url = this.stacURL + url;
                            }
                            break;
                        }
                    }
                }
            } catch (JacksonException e) {
                // try to skip this page and continue with the next one
                logger.severe("Failed to fetch page: " + url + " Error: " + e.getMessage());
                // avoid infinite loop if nextUrl keeps failing
                url = null; // or decide a fallback strategy
                continue;
            }
        }
        cumulatedResponse.setCollections(allCollections);
        return cumulatedResponse;
    }
    /**
     * Retrieves a single collection description from the remote STAC service
     * @param collectionName The name of the collection
     */
    public Collection getCollection(String collectionName) throws IOException {
        final HTTPResponse response = this.client.get(new URL(this.stacURL + "/collections/" + collectionName));
        try (InputStream inStream = response.getResponseStream()) {
            return STACParser.parseCollectionResponse(inStream);
        }
    }

    public QueryableList getGlobalParameters() throws IOException {
        final HTTPResponse response = this.client.get(new URL(this.stacURL + "/queryables"));
        try (InputStream inStream = response.getResponseStream()) {
            return STACParser.parseQueryablesResponse(inStream);
        }
    }

    public QueryableList getCollectionParameters(String collectionName) throws IOException {
        final HTTPResponse response = this.client.get(new URL(this.stacURL + "/collections/" + collectionName + "/queryables"));
        try (InputStream inStream = response.getResponseStream()) {
            return STACParser.parseQueryablesResponse(inStream);
        }
    }

    /**
     * Retrieves the list of items (only the first page) from a given collection
     * @param collectionName The name of the collection
     */
    public ItemCollection listItems(String collectionName) throws IOException {
        return listItems(collectionName, 0, 0);
    }
    /**
     * Retrieves a page of items from a given collection.
     * @param collectionName The name of the colleciton
     * @param pageNumber The page number (1-based)
     * @param pageSize The page size
     */
    public ItemCollection listItems(String collectionName, int pageNumber, int pageSize) throws IOException {
        String href = this.stacURL + "/collections/" + collectionName + "/items";
        if (pageNumber > 0 & pageSize > 0) {
            href += "?page=" + pageNumber + "&limit=" + pageSize;
        }
        final HTTPResponse response = this.client.get(new URL(href));
        try (InputStream inStream = response.getResponseStream()) {
            return STACParser.parseItemCollectionResponse(inStream);
        }
    }
    /**
     * Retrieves a single item from a collection.
     * @param collectionName    The collection name
     * @param itemId            The item identifier
     */
    public Item getItem(String collectionName, String itemId) throws IOException {
        String href = this.stacURL + "/collections/" + collectionName + "/items/" + itemId;
        final HTTPResponse response = this.client.get(new URL(href));
        try (InputStream inStream = response.getResponseStream()) {
            return STACParser.parseItemResponse(inStream);
        }
    }

    /**
     * Returns the first page of items that match the given parameters from a collection
     * @param collectionName    The collection name
     * @param parameters        The search criteria
     */
    public ItemCollection search(String collectionName, Map<String, Object> parameters) throws IOException {
        return search(collectionName, parameters, 0, 0);
    }
    /**
     * Returns a page of items that match the given parameters from a collection
     * @param collectionName    The collection name
     * @param parameters        The search criteria
     * @param pageNumber        The page number (1-based)
     * @param pageSize          The page size
     */
    public ItemCollection search(String collectionName, Map<String, Object> parameters, int pageNumber, int pageSize) throws IOException {
        if (parameters == null) {
            return listItems(collectionName);
        }
        final Catalog catalog = getCatalog();
        Link searchLink = catalog.getLinks().stream().filter(l -> "search".equals(l.getRel())).findFirst().orElse(null);
        if (searchLink == null) {
            // Maybe the link was not returned by the catalog, but the service exists
            searchLink = new Link();
            searchLink.setHref(this.stacURL + "search");
            //throw new IOException("Search not supported on catalog " + catalog.getId());
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
            return STACParser.parseItemCollectionResponse(inStream);
        }
    }

    /**
     * Downloads an entire item (all downloadable assets of the item)
     * @param item      The item
     * @param folder    The destination folder
     */
    public void download(Item item, Path folder) throws IOException {
        final Map<String, Asset> assets = item.getAssets();
        if (assets != null && !assets.isEmpty()) {
            final Path target = folder.resolve(item.getId());
            FileUtilities.createDirectories(target);
            try {
                if (this.progressListener != null) {
                    this.progressListener.started(folder.getFileName().toString());
                }
                for (Asset asset : assets.values()) {
                    download(asset, target);
                }
            } finally {
                if (this.progressListener != null) {
                    this.progressListener.ended();
                }
            }
        }
    }
    /**
     * Downloads an individual asset from an item.
     * @param asset     The asset
     * @param folder    The destination folder
     */
    public void download(Asset asset, Path folder) throws IOException {
        final String href = asset.getHref();
        final String name = href.substring(href.lastIndexOf('/') + 1);
        final HTTPResponse response = this.client.get(new URL(href));
        final String header = response.getResponseHeader("Content-Length");
        long length = 0;
        if (header != null) {
            length = Long.parseLong(header);
        }
        if (this.progressListener != null) {
            this.progressListener.subActivityStarted(name);
        }
        try (InputStream inStream = response.getResponseStream();
             OutputStream outStream = Files.newOutputStream(folder.resolve(name))) {
            InputStream stream = this.progressListener != null ? new ListenableInputStream(inStream, length, this.progressListener) : inStream;
            FileUtilities.copyStream(stream, outStream);
        } finally {
            if (this.progressListener != null) {
                this.progressListener.subActivityEnded(name);
            }
        }
    }
    /**
     * Downloads an individual asset given as a URL from an item.
     * @param href     The URL of the asset
     * @param folder   The destination folder
     * @return the full path for the downloaded asset
     */
    public Path download(String href, Path folder) throws IOException {
        String name = href.substring(href.lastIndexOf('/') + 1);
        final HTTPResponse response = this.client.get(new URL(href));
        String header = response.getResponseHeader("Content-Length");
        long length = 0;
        if (header != null) {
            length = Long.parseLong(header);
        }
        header = response.getResponseHeader("Content-Disposition");
        if (header != null && header.startsWith("attachment;")) {
            name = header.substring(header.indexOf('=') + 1);
        }
        if (this.progressListener != null) {
            this.progressListener.subActivityStarted(name);
        }
       if (name.startsWith("\"") && name.endsWith("\"")) {
            name = name.substring(1, name.length() - 1);
        }
       try (InputStream inStream = response.getResponseStream();
             OutputStream outStream = Files.newOutputStream(folder.resolve(name))) {
            InputStream stream = this.progressListener != null ? new ListenableInputStream(inStream, length, this.progressListener) : inStream;
            FileUtilities.copyStream(stream, outStream);
        } finally {
            if (this.progressListener != null) {
                this.progressListener.subActivityEnded(name);
            }
        }
       return folder.resolve(name);
    }
    /**
     * Returns an input stream for an asset
     * @param asset     The asset
     */
    public InputStream download(Asset asset) throws IOException {
        final HTTPResponse response = this.client.get(new URL(asset.getHref()));
        InputStream stream = response.getResponseStream();
        final String header = response.getResponseHeader("Content-Length");
        long length = 0;
        if (header != null) {
            length = Long.parseLong(header);
        }
        if (this.progressListener != null) {
            stream = new ListenableInputStream(stream, length, this.progressListener);
        }
        return stream;
    }
    /**
     * Returns an input stream for an asset given by its URL
     * @param href     The URL of the asset
     */
    public InputStream download(String href) throws IOException {
        final HTTPResponse response = this.client.get(new URL(href));
        InputStream stream = response.getResponseStream();
        final String header = response.getResponseHeader("Content-Length");
        long length = 0;
        if (header != null) {
            length = Long.parseLong(header);
        }
        if (this.progressListener != null) {
            stream = new ListenableInputStream(stream, length, this.progressListener);
        }
        return stream;
    }

    public void setProgressListener(DownloadProgressListener progressListener) {
        this.progressListener = progressListener;
    }
}
