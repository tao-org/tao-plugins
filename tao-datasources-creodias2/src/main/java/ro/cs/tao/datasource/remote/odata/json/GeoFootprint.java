package ro.cs.tao.datasource.remote.odata.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GeoFootprint {
    @JsonProperty("type")
    private String type;
    @JsonProperty("coordinates")
    private Object coordinates;

    @JsonProperty("type")
    public String getType() {
        return type;
    }
    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }
    @JsonProperty("coordinates")
    public Object getCoordinates() {
        return coordinates;
    }
    @JsonProperty("coordinates")
    public void setCoordinates(Object coordinates) {
        this.coordinates = coordinates;
    }
}
