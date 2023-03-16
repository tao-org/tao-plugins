package org.w3id.cwl.cwl1_2.utils;

import java.util.Map;
import java.util.Optional;

public interface Saveable {
    public default Map<Object, Object> save(boolean top, String baseUrl, boolean relativeUris) {
        System.out.println("Not implemented save method!");
        return null;
    }

    public default Map<Object, Object> save(boolean top, String baseUrl){
        return save(top, baseUrl, true);
    }

    public default Map<Object, Object> save(boolean top) {
        return save(top, "");
    }

    public default Map<Object, Object> save() {
        return save(false);
    }
}
