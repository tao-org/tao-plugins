package ro.cs.tao.datasource.remote.eumetsat.model;

import java.util.ArrayList;

public class Format {
    private String name;
    private String timeliness;
    private String averageFileSize;
    private String filenameConvention;
    private String frequency;
    private ArrayList<String> typicalFilename;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTimeliness() {
        return timeliness;
    }

    public void setTimeliness(String timeliness) {
        this.timeliness = timeliness;
    }

    public String getAverageFileSize() {
        return averageFileSize;
    }

    public void setAverageFileSize(String averageFileSize) {
        this.averageFileSize = averageFileSize;
    }

    public String getFilenameConvention() {
        return filenameConvention;
    }

    public void setFilenameConvention(String filenameConvention) {
        this.filenameConvention = filenameConvention;
    }

    public String getFrequency() {
        return frequency;
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
    }

    public ArrayList<String> getTypicalFilename() {
        return typicalFilename;
    }

    public void setTypicalFilename(ArrayList<String> typicalFilename) {
        this.typicalFilename = typicalFilename;
    }
}
