package ro.cs.tao.datasource.usgs.json.responses;

import java.util.List;

public class SearchResults {
    private int numberReturned;
    private int totalHits;
    private int firstRecord;
    private int lastRecord;
    private int nextRecord;
    private List<SearchResult> results;

    public int getNumberReturned() {
        return numberReturned;
    }

    public void setNumberReturned(int numberReturned) {
        this.numberReturned = numberReturned;
    }

    public int getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = totalHits;
    }

    public int getFirstRecord() {
        return firstRecord;
    }

    public void setFirstRecord(int firstRecord) {
        this.firstRecord = firstRecord;
    }

    public int getLastRecord() {
        return lastRecord;
    }

    public void setLastRecord(int lastRecord) {
        this.lastRecord = lastRecord;
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
