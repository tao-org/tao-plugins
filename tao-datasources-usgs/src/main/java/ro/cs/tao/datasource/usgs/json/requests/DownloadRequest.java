package ro.cs.tao.datasource.usgs.json.requests;

import ro.cs.tao.datasource.usgs.json.types.Download;

import java.util.ArrayList;
import java.util.List;

public class DownloadRequest extends BaseRequest {
    private List<Download> downloads;

    public List<Download> getDownloads() {
        return downloads;
    }

    public void setDownloads(List<Download> downloads) {
        this.downloads = downloads;
    }

    public void addDownload(Download download) {
        if (this.downloads == null) {
            this.downloads = new ArrayList<>();
        }
        this.downloads.add(download);
    }
}
