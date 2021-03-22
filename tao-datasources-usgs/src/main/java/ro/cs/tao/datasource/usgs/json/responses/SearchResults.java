package ro.cs.tao.datasource.usgs.json.responses;

import java.util.List;

public class SearchResults {
    private int recordsReturned;
    private int totalHits;
    private int startingNumber;
    private int numExcluded;
    private int nextRecord;
    private List<SearchResult> results;

    public int getRecordsReturned() {
        return recordsReturned;
    }

    public void setRecordsReturned(int recordsReturned) {
        this.recordsReturned = recordsReturned;
    }

    public int getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = totalHits;
    }

    public int getStartingNumber() {
        return startingNumber;
    }

    public void setStartingNumber(int startingNumber) {
        this.startingNumber = startingNumber;
    }

    public int getNumExcluded() {
        return numExcluded;
    }

    public void setNumExcluded(int numExcluded) {
        this.numExcluded = numExcluded;
    }

    public int getNextRecord() {
        return nextRecord;
    }

    public void setNextRecord(int nextRecord) {
        this.nextRecord = nextRecord;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public void setResults(List<SearchResult> results) {
        this.results = results;
    }
}
