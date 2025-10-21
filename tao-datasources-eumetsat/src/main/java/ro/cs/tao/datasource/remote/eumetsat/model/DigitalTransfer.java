package ro.cs.tao.datasource.remote.eumetsat.model;

import java.util.ArrayList;

public class DigitalTransfer {
    public String availability;
    public Online onLine;
    public ArrayList<Format> formats;
    public ArrayList<String> channels;
}
