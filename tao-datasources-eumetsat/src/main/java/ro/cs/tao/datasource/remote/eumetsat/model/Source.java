package ro.cs.tao.datasource.remote.eumetsat.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;

public class Source {
    private String $schema;
    private String hierarchyLevel;
    @JsonProperty("abstract")
    private String myabstract;
    private ArrayList<String> acronyms;
    private String creationDate;
    private ArrayList<DataApplication> dataApplications;
    private ArrayList<DataPolicy> dataPolicies;
    private String datasetLanguage;
    private String datasetTitle;
    private String dateStamp;
    private boolean dataTailorCustomisation;
    private ArrayList<DigitalTransfer> digitalTransfers;
    private ArrayList<Distributor> distributors;
    private boolean eumetsatData;
    private String id;
    private ArrayList<String> parameters;
    private String language;
    private Location location;
    private Originator originator;
    private String productStatus;
    private String processingMode;
    private ArrayList<String> spatialResolutions;
    private ArrayList<Satellite> satellites;
    private TemporalExtent temporalExtent;
    private ArrayList<Thumbnail> thumbnails;
    private LocalDateTime modified;
    private LocalDateTime youngs_created_on;
    private String youngs_used_mapping;
    private String fulltext;
    private String schemaOrgDefinition;

    public String get$schema() {
        return $schema;
    }

    public void set$schema(String $schema) {
        this.$schema = $schema;
    }

    public String getHierarchyLevel() {
        return hierarchyLevel;
    }

    public void setHierarchyLevel(String hierarchyLevel) {
        this.hierarchyLevel = hierarchyLevel;
    }

    public String getMyabstract() {
        return myabstract;
    }

    public void setMyabstract(String myabstract) {
        this.myabstract = myabstract;
    }

    public ArrayList<String> getAcronyms() {
        return acronyms;
    }

    public void setAcronyms(ArrayList<String> acronyms) {
        this.acronyms = acronyms;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public ArrayList<DataApplication> getDataApplications() {
        return dataApplications;
    }

    public void setDataApplications(ArrayList<DataApplication> dataApplications) {
        this.dataApplications = dataApplications;
    }

    public ArrayList<DataPolicy> getDataPolicies() {
        return dataPolicies;
    }

    public void setDataPolicies(ArrayList<DataPolicy> dataPolicies) {
        this.dataPolicies = dataPolicies;
    }

    public String getDatasetLanguage() {
        return datasetLanguage;
    }

    public void setDatasetLanguage(String datasetLanguage) {
        this.datasetLanguage = datasetLanguage;
    }

    public String getDatasetTitle() {
        return datasetTitle;
    }

    public void setDatasetTitle(String datasetTitle) {
        this.datasetTitle = datasetTitle;
    }

    public String getDateStamp() {
        return dateStamp;
    }

    public void setDateStamp(String dateStamp) {
        this.dateStamp = dateStamp;
    }

    public boolean isDataTailorCustomisation() {
        return dataTailorCustomisation;
    }

    public void setDataTailorCustomisation(boolean dataTailorCustomisation) {
        this.dataTailorCustomisation = dataTailorCustomisation;
    }

    public ArrayList<DigitalTransfer> getDigitalTransfers() {
        return digitalTransfers;
    }

    public void setDigitalTransfers(ArrayList<DigitalTransfer> digitalTransfers) {
        this.digitalTransfers = digitalTransfers;
    }

    public ArrayList<Distributor> getDistributors() {
        return distributors;
    }

    public void setDistributors(ArrayList<Distributor> distributors) {
        this.distributors = distributors;
    }

    public boolean isEumetsatData() {
        return eumetsatData;
    }

    public void setEumetsatData(boolean eumetsatData) {
        this.eumetsatData = eumetsatData;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ArrayList<String> getParameters() {
        return parameters;
    }

    public void setParameters(ArrayList<String> parameters) {
        this.parameters = parameters;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Originator getOriginator() {
        return originator;
    }

    public void setOriginator(Originator originator) {
        this.originator = originator;
    }

    public String getProductStatus() {
        return productStatus;
    }

    public void setProductStatus(String productStatus) {
        this.productStatus = productStatus;
    }

    public String getProcessingMode() {
        return processingMode;
    }

    public void setProcessingMode(String processingMode) {
        this.processingMode = processingMode;
    }

    public ArrayList<String> getSpatialResolutions() {
        return spatialResolutions;
    }

    public void setSpatialResolutions(ArrayList<String> spatialResolutions) {
        this.spatialResolutions = spatialResolutions;
    }

    public ArrayList<Satellite> getSatellites() {
        return satellites;
    }

    public void setSatellites(ArrayList<Satellite> satellites) {
        this.satellites = satellites;
    }

    public TemporalExtent getTemporalExtent() {
        return temporalExtent;
    }

    public void setTemporalExtent(TemporalExtent temporalExtent) {
        this.temporalExtent = temporalExtent;
    }

    public ArrayList<Thumbnail> getThumbnails() {
        return thumbnails;
    }

    public void setThumbnails(ArrayList<Thumbnail> thumbnails) {
        this.thumbnails = thumbnails;
    }

    public LocalDateTime getModified() {
        return modified;
    }

    public void setModified(LocalDateTime modified) {
        this.modified = modified;
    }

    public LocalDateTime getYoungs_created_on() {
        return youngs_created_on;
    }

    public void setYoungs_created_on(LocalDateTime youngs_created_on) {
        this.youngs_created_on = youngs_created_on;
    }

    public String getYoungs_used_mapping() {
        return youngs_used_mapping;
    }

    public void setYoungs_used_mapping(String youngs_used_mapping) {
        this.youngs_used_mapping = youngs_used_mapping;
    }

    public String getFulltext() {
        return fulltext;
    }

    public void setFulltext(String fulltext) {
        this.fulltext = fulltext;
    }

    public String getSchemaOrgDefinition() {
        return schemaOrgDefinition;
    }

    public void setSchemaOrgDefinition(String schemaOrgDefinition) {
        this.schemaOrgDefinition = schemaOrgDefinition;
    }
}
