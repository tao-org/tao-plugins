package ro.cs.tao.datasource.usgs.json.responses;

public class AvailableDownload {
    private String url;
    private String eulaCode;
    private int downloadId;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getEulaCode() {
        return eulaCode;
    }

    public void setEulaCode(String eulaCode) {
        this.eulaCode = eulaCode;
    }

    public int getDownloadId() {
        return downloadId;
    }

    public void setDownloadId(int downloadId) {
        this.downloadId = downloadId;
    }
}
