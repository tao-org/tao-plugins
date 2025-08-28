package ro.cs.tao.stac.core.model;

import java.util.List;

public class Instrument {
    private String platform;
    private List<String> instruments;
    private String constellation;
    private String mission;
    private Double gsd;

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public List<String> getInstruments() {
        return instruments;
    }

    public void setInstruments(List<String> instruments) {
        this.instruments = instruments;
    }

    public String getConstellation() {
        return constellation;
    }

    public void setConstellation(String constellation) {
        this.constellation = constellation;
    }

    public String getMission() {
        return mission;
    }

    public void setMission(String mission) {
        this.mission = mission;
    }

    public Double getGsd() {
        return gsd;
    }

    public void setGsd(Double gsd) {
        this.gsd = gsd;
    }
}
