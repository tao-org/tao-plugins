package ro.cs.tao.stac.core.model;

import ro.cs.tao.utils.HttpMethod;

import java.util.HashMap;
import java.util.Map;

public class Link {
    protected String href;
    protected String rel;
    protected String type;
    protected String title;
    protected String description;
    protected HttpMethod method;
    protected Map<String, Object> headers;
    protected String body;
    protected Boolean merge;

    /**
     * The location of the resource
     */
    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    /**
     * Relation type of the link
     */
    public String getRel() {
        return rel;
    }

    public void setRel(String rel) {
        this.rel = rel;
    }

    /**
     * The media type of the resource
     */
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Title of the resource
     */
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Specifies the HTTP method that the resource expects. Default is GET
     */
    public HttpMethod getMethod() {
        return method != null ? method : HttpMethod.GET;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    /**
     * Object key values pairs that map to headers
     */
    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public void addHeader(String key, Object value) {
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(key, value);
    }

    /**
     * For POST requests, the resource can specify the HTTP body as a JSON object
     */
    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    /**
     * This is only valid when the server is responding to POST request.
     * If merge is true, the client is expected to merge the body value
     * into the current request body before following the link.
     * This avoids passing large post bodies back and forth when following links,
     * particularly for navigating pages through the `POST /search` endpoint.
     *
     * NOTE: To support form encoding it is expected that a client be able to merge in
     * the key value pairs specified as JSON <code>{\"next\": \"token\"}</code> will become <code>&amp;next=token</code>
     */
    public Boolean getMerge() {
        if (this.merge == null) {
            this.merge = false;
        }
        return this.merge;
    }

    public void setMerge(Boolean merge) {
        this.merge = merge;
    }
}
