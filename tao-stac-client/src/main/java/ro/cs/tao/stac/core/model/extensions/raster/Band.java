package ro.cs.tao.stac.core.model.extensions.raster;

public class Band {
    private Number noData;
    private String sampling;
    private String dataType;
    private Integer bits_per_sample;
    private Number spatial_resolution;
    private Statistics statistics;
    private String unit;
    private Double scale;
    private Double offset;
    private Histogram histogram;

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Histogram getHistogram() {
        return histogram;
    }

    public void setHistogram(Histogram histogram) {
        this.histogram = histogram;
    }

    public Number getNoData() {
        return noData;
    }

    public void setNoData(Number noData) {
        this.noData = noData;
    }

    public Double getOffset() {
        return offset;
    }

    public void setOffset(Double offset) {
        this.offset = offset;
    }

    public String getSampling() {
        return sampling;
    }

    public void setSampling(String sampling) {
        this.sampling = sampling;
    }

    public Double getScale() {
        return scale;
    }

    public void setScale(Double scale) {
        this.scale = scale;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public void setStatistics(Statistics statistics) {
        this.statistics = statistics;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getBits_per_sample() {
        return bits_per_sample;
    }

    public void setBits_per_sample(Integer bits_per_sample) {
        this.bits_per_sample = bits_per_sample;
    }

    public Number getSpatial_resolution() {
        return spatial_resolution;
    }

    public void setSpatial_resolution(Number spatial_resolution) {
        this.spatial_resolution = spatial_resolution;
    }
}
