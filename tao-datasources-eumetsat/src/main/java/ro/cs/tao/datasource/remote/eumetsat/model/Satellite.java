package ro.cs.tao.datasource.remote.eumetsat.model;

import java.util.ArrayList;

public class Satellite {
    private String seriesName;
    private String owner;
    private String orbitType;
    private ArrayList<Sensor> sensors;

    public String getSeriesName() {
        return seriesName;
    }

    public void setSeriesName(String seriesName) {
        this.seriesName = seriesName;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOrbitType() {
        return orbitType;
    }

    public void setOrbitType(String orbitType) {
        this.orbitType = orbitType;
    }

    public ArrayList<Sensor> getSensors() {
        return sensors;
    }

    public void setSensors(ArrayList<Sensor> sensors) {
        this.sensors = sensors;
    }
}
