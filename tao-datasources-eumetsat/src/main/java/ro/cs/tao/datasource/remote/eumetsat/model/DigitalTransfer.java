package ro.cs.tao.datasource.remote.eumetsat.model;

import java.util.ArrayList;

public class DigitalTransfer {
    private String availability;
    private Online onLine;
    private ArrayList<Format> formats;
    private ArrayList<String> channels;

    public String getAvailability() {
        return availability;
    }

    public Online getOnLine() {
        return onLine;
    }

    public ArrayList<Format> getFormats() {
        return formats;
    }

    public ArrayList<String> getChannels() {
        return channels;
    }
}
