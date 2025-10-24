package ro.cs.tao.datasource.remote.eumetsat.model;

import java.util.ArrayList;

public class Location {
    private ArrayList<ArrayList<Integer>> coordinates;
    private String type;

    public ArrayList<ArrayList<Integer>> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(ArrayList<ArrayList<Integer>> coordinates) {
        this.coordinates = coordinates;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

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
