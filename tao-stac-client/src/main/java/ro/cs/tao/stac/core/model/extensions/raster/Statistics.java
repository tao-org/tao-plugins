package ro.cs.tao.stac.core.model.extensions.raster;

public class Statistics {
    private Double mean;
    private Double minimum;
    private Double maximum;
    private Double stdev;
    private Double valid_percent;

    public Double getMaximum() {
        return maximum;
    }

    public void setMaximum(Double maximum) {
        this.maximum = maximum;
    }

    public Double getMean() {
        return mean;
    }

    public void setMean(Double mean) {
        this.mean = mean;
    }

    public Double getMinimum() {
        return minimum;
    }

    public void setMinimum(Double minimum) {
        this.minimum = minimum;
    }

    public Double getStdev() {
        return stdev;
    }

    public void setStdev(Double stdev) {
        this.stdev = stdev;
    }

    public Double getValid_percent() {
        return valid_percent;
    }

    public void setValid_percent(Double validPercent) {
        this.valid_percent = validPercent;
    }
}
