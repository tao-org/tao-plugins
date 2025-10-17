package ro.cs.tao.opensearch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class OpenSearchDescription {
    private String shortName;
    private String description;
    private String tags;
    private String contact;
    private List<Url> url;
    private String longName;
    private Query query;
    private String developer;
    private String syndicationRight;
    private boolean adultContent;
    private String language;
    private String outputEncoding;
    private String inputEncoding;
    private String xs;
    private String xmlns;
    private String time;
    private String geo;
    private String eo;
    private String param;
    private String eum;
    private String sru;
    private String atom;
    private String text;

    @JsonProperty("ShortName")
    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    @JsonProperty("Description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
    @JsonProperty("Tags")
    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    @JsonProperty("Contact")
    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    @JsonProperty("Url")
    public List<Url> getUrl() {
        return url;
    }

    public void setUrl(List<Url> url) {
        this.url = url;
    }

    public void addUrl(Url url) {
        if (this.url == null) {
            this.url = new ArrayList<>();
        }
        this.url.add(url);
    }

    @JsonProperty("LongName")
    public String getLongName() {
        return longName;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }

    @JsonProperty("Query")
    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    @JsonProperty("Developer")
    public String getDeveloper() {
        return developer;
    }

    public void setDeveloper(String developer) {
        this.developer = developer;
    }

    @JsonProperty("SyndicationRight")
    public String getSyndicationRight() {
        return syndicationRight;
    }

    public void setSyndicationRight(String syndicationRight) {
        this.syndicationRight = syndicationRight;
    }

    @JsonProperty("AdultContent")
    public boolean isAdultContent() {
        return adultContent;
    }

    public void setAdultContent(boolean adultContent) {
        this.adultContent = adultContent;
    }

    @JsonProperty("Language")
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @JsonProperty("OutputEncoding")
    public String getOutputEncoding() {
        return outputEncoding;
    }

    public void setOutputEncoding(String outputEncoding) {
        this.outputEncoding = outputEncoding;
    }

    @JsonProperty("InputEncoding")
    public String getInputEncoding() {
        return inputEncoding;
    }

    public void setInputEncoding(String inputEncoding) {
        this.inputEncoding = inputEncoding;
    }
}
