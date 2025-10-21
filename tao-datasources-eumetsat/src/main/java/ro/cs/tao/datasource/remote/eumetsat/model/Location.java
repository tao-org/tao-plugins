package ro.cs.tao.datasource.remote.eumetsat.model;

import java.util.ArrayList;

public class Location {
    public ArrayList<ArrayList<Integer>> coordinates;
    public String type;

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        if (coordinates != null && !coordinates.isEmpty()) {
            builder.append("[");
            for (ArrayList<Integer> c : coordinates) {
                for (Integer v : c) {
                    builder.append(v).append(",");
                }
                builder.append(" ");
            }
            builder.setLength(builder.length() - 1);
            builder.append("]");
        }
        return builder.toString();
    }
}
