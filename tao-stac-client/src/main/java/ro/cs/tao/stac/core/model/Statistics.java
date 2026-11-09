package ro.cs.tao.stac.core.model;

public class Statistics {
    private Double minimum;
    private Double maximum;
    private Double mean;
    private Double stddev;
    private Double count;
    private Double valid_percent;

    public Double getMinimum() {
        return minimum;
    }

    public void setMinimum(Double minimum) {
        this.minimum = minimum;
    }

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

    public Double getStddev() {
        return stddev;
    }

    public void setStddev(Double stddev) {
        this.stddev = stddev;
    }

    public Double getCount() {
        return count;
    }

    public void setCount(Double count) {
        this.count = count;
    }

    public Double getValid_percent() {
        return valid_percent;
    }

    public void setValid_percent(Double valid_percent) {
        this.valid_percent = valid_percent;
    }
}
