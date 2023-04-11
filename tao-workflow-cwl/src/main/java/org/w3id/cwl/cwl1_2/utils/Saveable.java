package org.w3id.cwl.cwl1_2.utils;

import org.w3id.cwl.cwl1_2.CommandOutputBinding;

import java.lang.reflect.Field;
import java.util.*;

public interface Saveable {
    public default Map<Object, Object> save(boolean top, String baseUrl, boolean relativeUris) {
        Map<Object, Object> objectList = new LinkedHashMap<>();
        for (Field f : this.getClass().getDeclaredFields()) {
            if (!f.getName().equalsIgnoreCase("loadingOptions_") && !f.getName().equalsIgnoreCase("extensionFields_")) {
                f.setAccessible(true);
                try {
                    if (f.get(this) != null) {
                        if (f.get(this) instanceof Optional) {
                            objectList.put(f.getName(), ((Optional<?>) f.get(this)).get());
                        } else {
                            objectList.put(f.getName(), f.get(this).toString());
                        }
                    }
                } catch (Exception e) {
                    System.out.println(e.getStackTrace());
                }
            }
        }
        System.out.println("Interface implementation of the save method!");
        return objectList;
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

    /**
     * write input and output elements in a LinkedHashMap
     */
    public default Map<String, Object> treatInOut(java.util.List<Object> objects) {
        Map<String, Object> objectList = new LinkedHashMap<>();
        try {
            for (Object object : objects) {
                Field[] fields = object.getClass().getDeclaredFields();
                Map<String, Object> temp = new LinkedHashMap<>();
                String inputId = "";
                for (Field field : fields) {
                    if (!field.getName().equalsIgnoreCase("loadingOptions_") && !field.getName().equalsIgnoreCase("extensionFields_")) {
                        field.setAccessible(true);
                        if (field.getName().equalsIgnoreCase("id")) {
                            inputId = ((Optional)field.get(object)).get().toString();
                        } else if (field.get(object) != null) {
                            if (field.get(object) instanceof Optional<?> && ((Optional) field.get(object)).get() instanceof Saveable) {
                                temp.put(field.getName(), ((Saveable) ((Optional) field.get(object)).get()).save());
                            } else  if (field.get(object) instanceof Optional<?>) {
                                temp.put(field.getName(), ((Optional) field.get(object)).get());
                            } else {
                                temp.put(field.getName(), field.get(object));
                            }
                        }
                    }
                }
                if (inputId != "") {
                    objectList.put(inputId, temp);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return objectList;
    }
}
