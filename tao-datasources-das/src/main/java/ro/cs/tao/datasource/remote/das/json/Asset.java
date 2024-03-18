package ro.cs.tao.datasource.remote.das.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Asset {
    private String type;
    private String id;
    private String downloadLink;
    private String s3Path;

    @JsonProperty("Type")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("Id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("DownloadLink")
    public String getDownloadLink() {
        return downloadLink;
    }

    public void setDownloadLink(String downloadLink) {
        this.downloadLink = downloadLink;
    }

    @JsonProperty("S3Path")
    public String getS3Path() {
        return s3Path;
    }

    public void setS3Path(String s3Path) {
        this.s3Path = s3Path;
    }
}
