package ro.cs.tao.stac.core.model.extensions.raster;

public class Histogram {
    private Integer count;
    private Double min;
    private Double max;
    private Integer[] buckets;

    public Integer[] getBuckets() {
        return buckets;
    }

    public void setBuckets(Integer[] buckets) {
        this.buckets = buckets;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }
}
