package ro.cs.tao.datasource.usgs.json.requests;

public class CloudCoverFilter {
    private int min;
    private int max;
    private boolean includeUnknown;

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public boolean isIncludeUnknown() {
        return includeUnknown;
    }

    public void setIncludeUnknown(boolean includeUnknown) {
        this.includeUnknown = includeUnknown;
    }
}
