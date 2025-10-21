package ro.cs.tao.datasource.remote.eumetsat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;

public class Source {
    public String $schema;
    public String hierarchyLevel;
    @JsonProperty("abstract")
    public String myabstract;
    public ArrayList<String> acronyms;
    public String creationDate;
    public ArrayList<DataApplication> dataApplications;
    public ArrayList<DataPolicy> dataPolicies;
    public String datasetLanguage;
    public String datasetTitle;
    public String dateStamp;
    public boolean dataTailorCustomisation;
    public ArrayList<DigitalTransfer> digitalTransfers;
    public ArrayList<Distributor> distributors;
    public boolean eumetsatData;
    public String id;
    public ArrayList<String> parameters;
    public String language;
    public Location location;
    public Originator originator;
    public String productStatus;
    public String processingMode;
    public ArrayList<String> spatialResolutions;
    public ArrayList<Satellite> satellites;
    public TemporalExtent temporalExtent;
    public ArrayList<Thumbnail> thumbnails;
    public LocalDateTime modified;
    public LocalDateTime youngs_created_on;
    public String youngs_used_mapping;
    public String fulltext;
    public String schemaOrgDefinition;
}
