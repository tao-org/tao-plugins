package ro.cs.tao.datasource.remote.eumetsat.model;

import ro.cs.tao.utils.StringUtilities;

public class TemporalExtent {
    public String begin;
    public String end;

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
