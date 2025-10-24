package ro.cs.tao.datasource.remote.eumetsat.model;

import ro.cs.tao.utils.StringUtilities;

public class TemporalExtent {
    private String begin;
    private String end;

    public String getBegin() {
        return begin;
    }

    public void setBegin(String begin) {
        this.begin = begin;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        if (!StringUtilities.isNullOrEmpty(begin)) {
            builder.append("From ").append(begin).append(" ");
        }
        if (!StringUtilities.isNullOrEmpty(end)) {
            builder.append("To ").append(end);
        }
        return builder.toString();
    }
}
