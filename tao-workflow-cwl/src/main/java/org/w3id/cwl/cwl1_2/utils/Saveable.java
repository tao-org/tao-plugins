package org.w3id.cwl.cwl1_2.utils;

import java.util.Map;

public interface Saveable {
    default Map<Object, Object> save(boolean top, String baseUrl, boolean relativeUris) {
        return CWLSerializer.serialize(this);
    }
    default Map<Object, Object> save(boolean top, String baseUrl) { return save(top, baseUrl, true); }
    default Map<Object, Object> save(boolean top) { return save(top, ""); }
    default Map<Object, Object> save() { return save(false); }
}
