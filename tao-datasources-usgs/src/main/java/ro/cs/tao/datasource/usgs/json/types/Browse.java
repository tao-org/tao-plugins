package ro.cs.tao.datasource.usgs.json.types;

public class Browse {
    private String id;
    private String browseName;
    private String browsePath;
    private Boolean browseRotationEnabled;
    private String overlayPath;
    private String overlayType;
    private String thumbnailPath;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBrowseName() {
        return browseName;
    }

    public void setBrowseName(String browseName) {
        this.browseName = browseName;
    }

    public String getBrowsePath() {
        return browsePath;
    }

    public void setBrowsePath(String browsePath) {
        this.browsePath = browsePath;
    }

    public Boolean getBrowseRotationEnabled() {
        return browseRotationEnabled;
    }

    public void setBrowseRotationEnabled(Boolean browseRotationEnabled) {
        this.browseRotationEnabled = browseRotationEnabled;
    }

    public String getOverlayPath() {
        return overlayPath;
    }

    public void setOverlayPath(String overlayPath) {
        this.overlayPath = overlayPath;
    }

    public String getOverlayType() {
        return overlayType;
    }

    public void setOverlayType(String overlayType) {
        this.overlayType = overlayType;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }
}
