package ro.cs.tao.datasource.remote.eumetsat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Description {
    @JsonProperty("_index")
    private String _index;
    @JsonProperty("_id")
    private String _id;
    @JsonProperty("_version")
    private int _version;
    @JsonProperty("_seq_no")
    private int _seq_no;
    @JsonProperty("_primary_term")
    private int _primary_term;
    private boolean found;
    @JsonProperty("_source")
    private Source _source;

    @JsonProperty("_index")
    public String get_index() {
        return _index;
    }

    @JsonProperty("_index")
    public void set_index(String _index) {
        this._index = _index;
    }

    @JsonProperty("_id")
    public String get_id() {
        return _id;
    }

    @JsonProperty("_id")
    public void set_id(String _id) {
        this._id = _id;
    }

    @JsonProperty("_version")
    public int get_version() {
        return _version;
    }

    @JsonProperty("_version")
    public void set_version(int _version) {
        this._version = _version;
    }

    @JsonProperty("_seq_no")
    public int get_seq_no() {
        return _seq_no;
    }

    @JsonProperty("_seq_no")
    public void set_seq_no(int _seq_no) {
        this._seq_no = _seq_no;
    }

    @JsonProperty("_primary_term")
    public int get_primary_term() {
        return _primary_term;
    }

    @JsonProperty("_primary_term")
    public void set_primary_term(int _primary_term) {
        this._primary_term = _primary_term;
    }

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    @JsonProperty("_source")
    public Source get_source() {
        return _source;
    }

    @JsonProperty("_source")
    public void set_source(Source _source) {
        this._source = _source;
    }
}
