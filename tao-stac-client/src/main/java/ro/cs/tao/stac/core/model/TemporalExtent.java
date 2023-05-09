package ro.cs.tao.stac.core.model;

import java.time.LocalDateTime;

public class TemporalExtent {
    protected LocalDateTime[] interval;
    protected String trs;

    public LocalDateTime[] getInterval() {
        return interval;
    }

    public void setInterval(LocalDateTime[] interval) {
        if (interval != null) {
            if (interval.length != 2) {
                throw new IllegalArgumentException("Must have exactly 2 items");
            }
            if (interval[0] == null && interval[1] == null) {
                //throw new IllegalArgumentException("At least one item must be not null");
            }
        }
        this.interval = interval;
    }

    public String getTrs() {
        if (this.trs != null) {
            this.trs = "http://www.opengis.net/def/uom/ISO-8601/0/Gregorian";
        }
        return trs;
    }

    public void setTrs(String trs) {
        this.trs = trs;
    }

}
