package ro.cs.tao.opensearch;

import org.geotools.http.HTTPResponse;
import ro.cs.tao.ListenableInputStream;
import ro.cs.tao.component.WebServiceAuthentication;
import ro.cs.tao.eodata.EOProduct;
import ro.cs.tao.opensearch.model.Entry;
import ro.cs.tao.opensearch.model.Link;
import ro.cs.tao.opensearch.model.OpenSearchDescription;
import ro.cs.tao.utils.FileUtilities;
import ro.cs.tao.utils.executors.monitoring.DownloadProgressListener;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Client for a STAC web service.
 * It allows for:
 * - browsing various STAC items (catalog, collections, items)
 * - searching collections
 * - downloading individual assets or full items
 *
 * @author Cosmin Cara
 */
public class OpenSearchClient {
    private final String baseUrl;
    final HttpClient client;
    private DownloadProgressListener progressListener;

    /**
     * Initializes a new client for the given URL, with specific authentication instructions.
     * @param baseURL           The URL of the STAC web service
     * @param authentication    The authentication scheme
     */
    public OpenSearchClient(String baseURL, WebServiceAuthentication authentication) throws MalformedURLException {
        this.baseUrl = baseURL;
        this.client= new HttpClient(authentication);
    }

    /**
     * Retrieves the catalog description from the remote STAC service
     */
    public OpenSearchDescription getDescription() throws Exception {
        final HTTPResponse response = this.client.get(new URL(this.baseUrl + "osdd"));
        try (InputStream inStream = response.getResponseStream()) {
            return new ro.cs.tao.opensearch.OpenSearchDescriptionParser().parse(inStream);
        }
    }

    /**
     * Returns the first page of items that match the given parameters from a collection
     * @param collectionName    The collection name
     * @param parameters        The search criteria
     */
    public List<EOProduct> search(String collectionName, Map<String, Object> parameters) throws Exception {
        return search(collectionName, parameters, 0, 0);
    }
    /**
     * Returns a page of items that match the given parameters from a collection
     * @param collectionName    The collection name
     * @param parameters        The search criteria
     * @param pageNumber        The page number (1-based)
     * @param pageSize          The page size
     */
    public List<EOProduct> search(String collectionName, Map<String, Object> parameters, int pageNumber, int pageSize) throws Exception {
        if (parameters == null) {
            parameters = Map.of();
        }
        StringBuilder href = new StringBuilder(this.baseUrl + "os?");
        href.append("pi=").append(collectionName).append("&");
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            href.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        if (pageNumber > 0 & pageSize > 0) {
            href.append("page=").append(pageSize).append("&limit=").append(pageSize);
        }
        if (href.charAt(href.length() - 1) == '&') {
            href.setLength(href.length() - 1);
        }
        final HTTPResponse response = this.client.get(new URL(href.toString()));
        try (InputStream inStream = response.getResponseStream()) {
            return new ro.cs.tao.opensearch.SearchResultsParser().parse(inStream);
        }
    }

    /**
     * Downloads an entire item (all downloadable assets of the item)
     * @param item      The item
     * @param folder    The destination folder
     */
    public void download(Entry item, Path folder) throws IOException {
        final List<Link> assets = item.getLink();
        if (assets != null && !assets.isEmpty()) {
            final Path target = folder.resolve(item.getId());
            FileUtilities.createDirectories(target);
            try {
                if (this.progressListener != null) {
                    this.progressListener.started(folder.getFileName().toString());
                }
                for (Link asset : assets) {
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
    public void download(Link asset, Path folder) throws IOException {
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
     */
    public void download(String href, Path folder) throws IOException {
        String name = href.substring(href.lastIndexOf('/') + 1);
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
     * Returns an input stream for an asset
     * @param asset     The asset
     */
    public InputStream download(Link asset) throws IOException {
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
