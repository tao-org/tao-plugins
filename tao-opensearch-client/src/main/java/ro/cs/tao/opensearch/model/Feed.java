package ro.cs.tao.opensearch.model;

import java.time.LocalDateTime;
import java.util.List;

public class Feed {
    private String title;
    private String subtitle;
    private String id;
    private Generator generator;
    private Author author;
    private Category category;
    private Contributor contributor;
    private String rights;
    private LocalDateTime updated;
    private String subject;
    private List<Link> link;
    private int totalResults;
    private int itemsPerPage;
    private int startIndex;
    private Query Query;
    private ExtraInformation extraInformation;
    private List<Entry> entry;
    private String xs;
    private String atom;
    private String opensearch;
    private String geo;
    private String time;
    private String eum;
    private String eo;
    private String sru;
    private String georss;
    private String gml;
    private String dc;
    private String media;
    private String lang;
    private String text;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Generator getGenerator() {
        return generator;
    }

    public void setGenerator(Generator generator) {
        this.generator = generator;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Contributor getContributor() {
        return contributor;
    }

    public void setContributor(Contributor contributor) {
        this.contributor = contributor;
    }

    public String getRights() {
        return rights;
    }

    public void setRights(String rights) {
        this.rights = rights;
    }

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public List<Link> getLink() {
        return link;
    }

    public void setLink(List<Link> link) {
        this.link = link;
    }

    public int getTotalResults() {
        return totalResults;
    }

    public void setTotalResults(int totalResults) {
        this.totalResults = totalResults;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public void setItemsPerPage(int itemsPerPage) {
        this.itemsPerPage = itemsPerPage;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }

    public Query getQuery() {
        return Query;
    }

    public void setQuery(Query query) {
        Query = query;
    }

    public ExtraInformation getExtraInformation() {
        return extraInformation;
    }

    public void setExtraInformation(ExtraInformation extraInformation) {
        this.extraInformation = extraInformation;
    }

    public List<Entry> getEntry() {
        return entry;
    }

    public void setEntry(List<Entry> entry) {
        this.entry = entry;
    }

    public String getXs() {
        return xs;
    }

    public void setXs(String xs) {
        this.xs = xs;
    }

    public String getAtom() {
        return atom;
    }

    public void setAtom(String atom) {
        this.atom = atom;
    }

    public String getOpensearch() {
        return opensearch;
    }

    public void setOpensearch(String opensearch) {
        this.opensearch = opensearch;
    }

    public String getGeo() {
        return geo;
    }

    public void setGeo(String geo) {
        this.geo = geo;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getEum() {
        return eum;
    }

    public void setEum(String eum) {
        this.eum = eum;
    }

    public String getEo() {
        return eo;
    }

    public void setEo(String eo) {
        this.eo = eo;
    }

    public String getSru() {
        return sru;
    }

    public void setSru(String sru) {
        this.sru = sru;
    }

    public String getGeorss() {
        return georss;
    }

    public void setGeorss(String georss) {
        this.georss = georss;
    }

    public String getGml() {
        return gml;
    }

    public void setGml(String gml) {
        this.gml = gml;
    }

    public String getDc() {
        return dc;
    }

    public void setDc(String dc) {
        this.dc = dc;
    }

    public String getMedia() {
        return media;
    }

    public void setMedia(String media) {
        this.media = media;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
