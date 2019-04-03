package ro.cs.tao.datasource.usgs.json.requests;

public class SearchFilterBetween extends SearchFilter {
    private String firstValue;
    private String secondValue;

    public SearchFilterBetween() {
        setFilterType("between");
    }

    public String getFirstValue() {
        return firstValue;
    }

    public void setFirstValue(String firstValue) {
        this.firstValue = firstValue;
    }

    public String getSecondValue() {
        return secondValue;
    }

    public void setSecondValue(String secondValue) {
        this.secondValue = secondValue;
    }
}
